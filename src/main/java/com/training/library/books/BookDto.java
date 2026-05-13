package com.training.library.books;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public interface BookDto {

  // Used by POST (create). ISBN is required and immutable — only settable at creation time.
  // count uses @PositiveOrZero (allowing 0) because the "must be > 0" rule was scoped to
  // updates; a fresh book with no copies is a legitimate creation state.
  record WriteRequest(
      @NotBlank
          @Pattern(
              regexp = "^(\\d{9}[\\dX]|\\d{13})$",
              message = "must be a valid ISBN-10 or ISBN-13")
          String isbn,
      @NotBlank @Size(min = 1, max = 1000) String title,
      @NotNull @PositiveOrZero @Max(100_000) Integer count) {}

  // Used by PUT (full replace of mutable fields). count must be > 0 (@Positive); the
  // additional "count >= active loans" check is dynamic and lives in the service.
  record UpdateRequest(
      @NotBlank @Size(min = 1, max = 1000) String title,
      @NotNull @Positive @Max(100_000) Integer count) {}

  // Used by PATCH — every field is optional. Constraints fire only when the value is
  // present. count, when present, must be > 0 (@Positive is null-tolerant).
  record PatchRequest(
      @Size(min = 1, max = 1000) String title, @Positive @Max(100_000) Integer count) {}

  // Outbound response shape. count = total inventory; available_count = inventory minus
  // active (un-returned, not soft-deleted) loans. created_at / updated_at are audit
  // metadata; deleted_at stays internal.
  record Response(
      Long id,
      String isbn,
      String title,
      int count,
      @JsonProperty("available_count") int availableCount,
      @JsonProperty("created_at") Instant createdAt,
      @JsonProperty("updated_at") Instant updatedAt) {}

  // Root-key envelopes. Every request/response wraps its payload under "book" / "books"
  // so the wire format is self-describing and stays stable as we add sibling fields later.
  // @Valid on the inner field cascades Bean Validation into the wrapped DTO.
  record WriteEnvelope(@Valid @NotNull WriteRequest book) {}

  record UpdateEnvelope(@Valid @NotNull UpdateRequest book) {}

  record PatchEnvelope(@Valid @NotNull PatchRequest book) {}

  record ResponseEnvelope(Response book) {}

  record ListEnvelope(List<Response> books, Meta meta) {}

  // Pagination meta. next_page / prev_page are omitted when there is no neighbor.
  @JsonInclude(Include.NON_NULL)
  record Meta(
      long total,
      @JsonProperty("next_page") Integer nextPage,
      @JsonProperty("prev_page") Integer prevPage) {}
}
