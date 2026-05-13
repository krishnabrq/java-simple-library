package com.training.library.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.training.library.common.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

// Web security baseline:
// - Stateless JWT bearer auth (no sessions, no CSRF token cookie).
// - /api/v1/auth/** is public (signup, login, refresh).
// - Everything else requires a valid access token. Fine-grained role checks live on
//   controllers via @PreAuthorize (enabled by @EnableMethodSecurity below).
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  // Shared HS256 secret. Must be ≥32 bytes (256 bits). No default — Spring fails fast at
  // startup if jwt.secret isn't supplied, surfacing the misconfiguration immediately.
  private final byte[] secretBytes;

  public SecurityConfig(@Value("${jwt.secret}") String secret) {
    this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (secretBytes.length < 32) {
      throw new IllegalStateException(
          "jwt.secret must be at least 32 bytes (256 bits) for HS256; got "
              + secretBytes.length
              + " bytes");
    }
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    // Default strength = 10 rounds. Bumping to 12 in real prod when CPU budget allows.
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain api(
      HttpSecurity http,
      JwtAuthenticationConverter jwtAuthConverter,
      AuthenticationEntryPoint authenticationEntryPoint,
      AccessDeniedHandler accessDeniedHandler)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/v1/auth/**")
                    .permitAll()
                    // Spring's internal error dispatch goes through /error before our
                    // @RestControllerAdvice replies. Permit it so 4xx bodies aren't masked
                    // by a 401 from the security chain.
                    .requestMatchers("/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            rs ->
                rs.jwt(j -> j.jwtAuthenticationConverter(jwtAuthConverter))
                    // Resource-server sets its own entry point + handler by default; we
                    // override both to emit our ErrorResponse JSON shape.
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .exceptionHandling(
            eh ->
                eh.authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler));
    return http.build();
  }

  // 401 — caller is anonymous or sent an invalid/expired/missing bearer token.
  @Bean
  public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
    return (request, response, ex) ->
        writeError(
            response,
            objectMapper,
            HttpServletResponse.SC_UNAUTHORIZED,
            "Unauthorized",
            "Authentication is required to access this resource");
  }

  // 403 — caller is authenticated, but their authorities don't satisfy @PreAuthorize.
  // Distinct from LoanNotPermittedException (service-layer 403); both end up as 403 JSON
  // with the same envelope shape, just thrown from different layers.
  @Bean
  public AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
    return (request, response, ex) ->
        writeError(
            response,
            objectMapper,
            HttpServletResponse.SC_FORBIDDEN,
            "Forbidden",
            "Your role does not permit this action");
  }

  private static void writeError(
      HttpServletResponse response,
      ObjectMapper objectMapper,
      int status,
      String error,
      String message)
      throws java.io.IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(), ErrorResponse.of(status, error, message));
  }

  // HS256 keypair-of-one. Same key signs (encoder) and verifies (decoder).
  // Wrapped as a Nimbus JWK so NimbusJwtEncoder can pick the signing key by algorithm.
  private OctetSequenceKey jwk() {
    return new OctetSequenceKey.Builder(new SecretKeySpec(secretBytes, "HmacSHA256"))
        .keyID("library-hs256")
        .algorithm(JWSAlgorithm.HS256)
        .build();
  }

  @Bean
  public JwtEncoder jwtEncoder() {
    JWKSource<SecurityContext> jwks = new ImmutableJWKSet<>(new JWKSet(jwk()));
    return new NimbusJwtEncoder(jwks);
  }

  @Bean
  public JwtDecoder jwtDecoder() {
    return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secretBytes, "HmacSHA256"))
        .macAlgorithm(MacAlgorithm.HS256)
        .build();
  }

  // Maps the JWT's `role` claim ("MEMBER" / "STAFF") to a Spring `ROLE_<value>` authority,
  // so `@PreAuthorize("hasRole('STAFF')")` works downstream. Default converter reads
  // `scope`/`scp` claims — we don't use scopes, so we wire an explicit converter.
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          String role = jwt.getClaimAsString("role");
          if (role == null || role.isBlank()) {
            return List.of();
          }
          return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
    return converter;
  }
}
