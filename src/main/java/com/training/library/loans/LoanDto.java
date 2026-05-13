package com.training.library.loans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

public interface LoanDto {

  record BorrowRequest(@JsonProperty("book_id") @NotNull @Positive Long bookId) {}

  record Response(
      Long id,
      @JsonProperty("book_id") Long bookId,
      @JsonProperty("user_id") Long userId,
      @JsonProperty("borrowed_at") Instant borrowedAt,
      @JsonProperty("returned_at") Instant returnedAt) {}

  record BorrowEnvelope(@Valid @NotNull BorrowRequest loan) {}

  record ResponseEnvelope(Response loan) {}

  record BookSummary(Long id, String name) {}

  record ListResponse(
      Long id,
      BookSummary book,
      @JsonProperty("borrowed_at") Instant borrowedAt,
      @JsonProperty("returned_at") Instant returnedAt) {}

  record ListEnvelope(java.util.List<ListResponse> loans, Meta meta) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record Meta(
      long total,
      @JsonProperty("next_page") Integer nextPage,
      @JsonProperty("prev_page") Integer prevPage) {}
}
