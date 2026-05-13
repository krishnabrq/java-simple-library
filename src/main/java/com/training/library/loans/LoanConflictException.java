package com.training.library.loans;

// 409 — operation collides with current loan / book state. Cases:
//   * Borrowing a book with no available copies (all in active loans).
//   * Returning a loan that is already returned.
public class LoanConflictException extends RuntimeException {

  public LoanConflictException(String message) {
    super(message);
  }
}
