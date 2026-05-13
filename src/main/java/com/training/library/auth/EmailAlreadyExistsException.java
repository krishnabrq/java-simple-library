package com.training.library.auth;

// Signup hit an active user with the same email. Maps to 409 Conflict.
public class EmailAlreadyExistsException extends RuntimeException {

  public EmailAlreadyExistsException(String email) {
    super("Email already registered: " + email);
  }
}
