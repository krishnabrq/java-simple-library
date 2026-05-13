package com.training.library.loans;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

public interface LoanDto {

  // Borrow body. The caller is identified by the JWT — no user_id in the body.
  record BorrowRequest(@JsonProperty("book_id") @NotNull @Positive Long bookId) {}

  // Outbound shape. book_id / user_id are surfaced as IDs (not nested entities) so the
  // response is small and FK joins stay opaque to clients.
  record Response(
      Long id,
      @JsonProperty("book_id") Long bookId,
      @JsonProperty("user_id") Long userId,
      @JsonProperty("borrowed_at") Instant borrowedAt,
      @JsonProperty("returned_at") Instant returnedAt) {}

  record BorrowEnvelope(@Valid @NotNull BorrowRequest loan) {}

  record ResponseEnvelope(Response loan) {}
}
