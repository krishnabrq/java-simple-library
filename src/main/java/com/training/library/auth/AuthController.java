package com.training.library.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  // Cached at construction so we don't ask JwtService for it on every request.
  private static final String BEARER = "Bearer";

  private final AuthService authService;
  private final long accessTtlSeconds;

  public AuthController(AuthService authService, JwtService jwtService) {
    this.authService = authService;
    this.accessTtlSeconds = jwtService.accessTtlSeconds();
  }

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthDto.TokenEnvelope signup(@Valid @RequestBody AuthDto.SignupEnvelope envelope) {
    return toEnvelope(authService.signup(envelope.signup()));
  }

  @PostMapping("/login")
  public AuthDto.TokenEnvelope login(@Valid @RequestBody AuthDto.LoginEnvelope envelope) {
    return toEnvelope(authService.login(envelope.login()));
  }

  @PostMapping("/refresh")
  public AuthDto.TokenEnvelope refresh(@Valid @RequestBody AuthDto.RefreshEnvelope envelope) {
    return toEnvelope(authService.refresh(envelope.refresh().refreshToken()));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout() {
    authService.logout();
  }

  private AuthDto.TokenEnvelope toEnvelope(AuthService.TokenPair pair) {
    return new AuthDto.TokenEnvelope(
        new AuthDto.TokenResponse(pair.access(), pair.refresh(), BEARER, accessTtlSeconds));
  }
}
