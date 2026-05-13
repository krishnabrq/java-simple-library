package com.training.library.loans;

public class LoanNotPermittedException extends RuntimeException {

  public LoanNotPermittedException(String message) {
    super(message);
  }
}
