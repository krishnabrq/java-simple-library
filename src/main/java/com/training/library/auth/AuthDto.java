package com.training.library.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public interface AuthDto {

  record SignupRequest(
      @NotBlank @Size(min = 1, max = 255) String name,
      @NotBlank @Email @Size(max = 255) String email,
      @NotBlank @Size(min = 8, max = 100) String password) {}

  record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

  record RefreshRequest(@JsonProperty("refresh_token") @NotBlank String refreshToken) {}

  record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("refresh_token") String refreshToken,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("expires_in") long expiresIn) {}

  record SignupEnvelope(@Valid @NotNull SignupRequest signup) {}

  record LoginEnvelope(@Valid @NotNull LoginRequest login) {}

  record RefreshEnvelope(@Valid @NotNull RefreshRequest refresh) {}

  record TokenEnvelope(TokenResponse tokens) {}
}
