package com.training.library.auth;

import com.training.library.users.UserEntity;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

// Issues HS256-signed access + refresh tokens. The two share a shape; only `type` and TTL
// differ. The `type` claim lets endpoints reject a refresh token presented as an access
// token (and vice versa) without bookkeeping in the DB.
@Service
public class JwtService {

  public static final String CLAIM_ROLE = "role";
  public static final String CLAIM_EMAIL = "email";
  public static final String CLAIM_TYPE = "type";
  public static final String TYPE_ACCESS = "access";
  public static final String TYPE_REFRESH = "refresh";

  private final JwtEncoder encoder;
  private final Duration accessTtl;
  private final Duration refreshTtl;

  public JwtService(
      JwtEncoder encoder,
      @Value("${jwt.access-ttl}") Duration accessTtl,
      @Value("${jwt.refresh-ttl}") Duration refreshTtl) {
    this.encoder = encoder;
    this.accessTtl = accessTtl;
    this.refreshTtl = refreshTtl;
  }

  public String issueAccessToken(UserEntity user) {
    return issue(user, TYPE_ACCESS, accessTtl);
  }

  public String issueRefreshToken(UserEntity user) {
    return issue(user, TYPE_REFRESH, refreshTtl);
  }

  // Exposed for the `expires_in` field in TokenResponse — OAuth2 convention is seconds.
  public long accessTtlSeconds() {
    return accessTtl.toSeconds();
  }

  private String issue(UserEntity user, String type, Duration ttl) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("library")
            .subject(user.getId().toString())
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .claim(CLAIM_EMAIL, user.getEmail())
            .claim(CLAIM_ROLE, user.getRole().name())
            .claim(CLAIM_TYPE, type)
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
