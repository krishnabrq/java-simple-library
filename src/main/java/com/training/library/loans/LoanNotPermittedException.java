package com.training.library.loans;

// 403 — caller is authenticated but their role can't perform this action.
// Staff can't borrow or return; this is checked in the service layer against the live
// role in the DB (not the JWT claim, which can be stale).
public class LoanNotPermittedException extends RuntimeException {

  public LoanNotPermittedException(String message) {
    super(message);
  }
}
