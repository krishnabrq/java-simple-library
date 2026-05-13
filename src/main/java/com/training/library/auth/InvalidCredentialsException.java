package com.training.library.auth;

// Login failed (unknown email or wrong password). Maps to 401 Unauthorized. Carries the
// same message either way so the API doesn't leak whether the email exists.
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("Invalid email or password");
  }
}
