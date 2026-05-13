package com.training.library.auth;

// Refresh failed — token malformed, expired, wrong type, or the user it points at no
// longer exists. Maps to 401 Unauthorized.
public class InvalidTokenException extends RuntimeException {

  public InvalidTokenException(String message) {
    super(message);
  }
}
