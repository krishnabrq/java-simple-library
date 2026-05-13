package com.training.library.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.training.library.users.UserEntity;
import com.training.library.users.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

  private static final String SECRET = "unit-test-secret-32-bytes-of-padding!!";
  private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
  private static final Duration REFRESH_TTL = Duration.ofDays(7);

  private JwtService service;
  private JwtDecoder decoder;

  @BeforeEach
  void setUp() {
    byte[] keyBytes = SECRET.getBytes(StandardCharsets.UTF_8);
    SecretKeySpec key = new SecretKeySpec(keyBytes, "HmacSHA256");

    OctetSequenceKey jwk =
        new OctetSequenceKey.Builder(key).keyID("unit-test").algorithm(JWSAlgorithm.HS256).build();
    JWKSource<SecurityContext> jwks = new ImmutableJWKSet<>(new JWKSet(jwk));

    NimbusJwtEncoder encoder = new NimbusJwtEncoder(jwks);
    this.service = new JwtService(encoder, ACCESS_TTL, REFRESH_TTL);
    this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
  }

  private static UserEntity user(Long id, String email, UserRole role) {
    UserEntity u = new UserEntity();
    u.setName("Test");
    u.setEmail(email);
    u.setPasswordHash("not-real");
    u.setRole(role);
    // UserEntity has no setId by design (id is DB-assigned). Reflection here keeps the
    // production entity free of a footgun setter while letting this unit test exercise the
    // id-dependent `sub` claim without booting the JPA / persistence stack.
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  @Test
  @DisplayName("issueAccessToken - encodes role/email/type=access and respects access TTL")
  void issueAccessToken_carriesExpectedClaims() {
    UserEntity u = user(42L, "alice@e.test", UserRole.MEMBER);
    Instant before = Instant.now();

    String token = service.issueAccessToken(u);
    Jwt jwt = decoder.decode(token);

    assertThat(jwt.getClaimAsString(JwtService.CLAIM_TYPE)).isEqualTo("access");
    assertThat(jwt.getClaimAsString(JwtService.CLAIM_ROLE)).isEqualTo("MEMBER");
    assertThat(jwt.getClaimAsString(JwtService.CLAIM_EMAIL)).isEqualTo("alice@e.test");
    assertThat(jwt.getSubject()).isEqualTo("42");
    // Use getClaimAsString — Jwt.getIssuer() parses to a URL and returns null for non-URI
    // issuers like "library".
    assertThat(jwt.getClaimAsString("iss")).isEqualTo("library");
    // Slack on either side for clock + JWT encoder overhead.
    assertThat(jwt.getExpiresAt())
        .isBetween(before.plus(ACCESS_TTL).minusSeconds(2), Instant.now().plus(ACCESS_TTL));
  }

  @Test
  @DisplayName("issueRefreshToken - encodes type=refresh and uses the longer TTL")
  void issueRefreshToken_carriesExpectedClaims() {
    UserEntity u = user(99L, "staff@e.test", UserRole.STAFF);
    Instant before = Instant.now();

    String token = service.issueRefreshToken(u);
    Jwt jwt = decoder.decode(token);

    assertThat(jwt.getClaimAsString(JwtService.CLAIM_TYPE)).isEqualTo("refresh");
    assertThat(jwt.getClaimAsString(JwtService.CLAIM_ROLE)).isEqualTo("STAFF");
    assertThat(jwt.getSubject()).isEqualTo("99");
    assertThat(jwt.getExpiresAt())
        .isBetween(before.plus(REFRESH_TTL).minusSeconds(2), Instant.now().plus(REFRESH_TTL));
  }

  @Test
  @DisplayName("accessTtlSeconds - returns the access TTL converted to seconds")
  void accessTtlSeconds_matchesConfig() {
    assertThat(service.accessTtlSeconds()).isEqualTo(ACCESS_TTL.toSeconds());
  }
}
