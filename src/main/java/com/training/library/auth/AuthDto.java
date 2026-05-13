package com.training.library.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public interface AuthDto {

  // Self-signup is members-only — role is fixed at MEMBER in the service. Staff accounts
  // get seeded manually via SQL (documented in AGENTS.md / PROGRESS.md).
  record SignupRequest(
      @NotBlank @Size(min = 1, max = 255) String name,
      @NotBlank @Email @Size(max = 255) String email,
      @NotBlank @Size(min = 8, max = 100) String password) {}

  record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

  // Snake_case on the wire; camelCase in Java.
  record RefreshRequest(@JsonProperty("refresh_token") @NotBlank String refreshToken) {}

  // OAuth2-style token envelope. token_type is always "Bearer" today; expires_in is the
  // access token's TTL in seconds. Refresh token has its own (longer) TTL — clients can
  // pre-emptively refresh based on expires_in.
  record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("refresh_token") String refreshToken,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("expires_in") long expiresIn) {}

  // Root-key envelopes — match the {"book": {...}} pattern used by the books module so
  // wire shape stays self-describing across features.
  record SignupEnvelope(@Valid @NotNull SignupRequest signup) {}

  record LoginEnvelope(@Valid @NotNull LoginRequest login) {}

  record RefreshEnvelope(@Valid @NotNull RefreshRequest refresh) {}

  record TokenEnvelope(TokenResponse tokens) {}
}
