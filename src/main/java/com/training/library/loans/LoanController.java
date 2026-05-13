package com.training.library.loans;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans")
public class LoanController {

  private final LoanService service;
  private final LoanMapper mapper;

  public LoanController(LoanService service, LoanMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  // No @PreAuthorize: the MEMBER-only rule is enforced by LoanService against the live
  // DB role, not the (potentially stale) JWT claim. Anyone authenticated reaches the
  // service, which then rejects STAFF with a 403.
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public LoanDto.ResponseEnvelope borrow(
      @Valid @RequestBody LoanDto.BorrowEnvelope envelope, @AuthenticationPrincipal Jwt jwt) {
    BookLoanEntity loan = service.borrow(subjectAsUserId(jwt), envelope.loan().bookId());
    return new LoanDto.ResponseEnvelope(mapper.toResponse(loan));
  }

  @PatchMapping("/{loanId}/return")
  public LoanDto.ResponseEnvelope returnLoan(
      @PathVariable @Min(1) Long loanId, @AuthenticationPrincipal Jwt jwt) {
    BookLoanEntity loan = service.returnLoan(subjectAsUserId(jwt), loanId);
    return new LoanDto.ResponseEnvelope(mapper.toResponse(loan));
  }

  private static Long subjectAsUserId(Jwt jwt) {
    // JwtService writes sub as user.id.toString(); any drift here means we minted a token
    // we can't read back — surface as a parse error rather than a silent NPE downstream.
    return Long.valueOf(jwt.getSubject());
  }
}
