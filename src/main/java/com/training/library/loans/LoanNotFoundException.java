package com.training.library.loans;

// 404 — loan id doesn't exist, OR exists but doesn't belong to the calling user.
// Same status either way: refusing to disclose loans belonging to other members.
public class LoanNotFoundException extends RuntimeException {

  public LoanNotFoundException(Long id) {
    super("Loan not found with id: " + id);
  }
}
