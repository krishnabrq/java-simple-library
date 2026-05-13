package com.training.library.books;

// Thrown when a book mutation collides with current state (e.g. delete-with-active-loans
// or count-below-active-loans). Maps to HTTP 409 Conflict in GlobalExceptionHandler.
public class BookConflictException extends RuntimeException {

  public BookConflictException(String message) {
    super(message);
  }
}
