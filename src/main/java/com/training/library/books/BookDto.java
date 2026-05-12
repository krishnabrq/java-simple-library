package com.training.library.books;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public interface BookDto {

  // Used by POST (create) and PUT (full replace) — all fields required.
  record WriteRequest(
      @NotBlank @Size(min = 1, max = 1000) String title,
      @NotNull @PositiveOrZero @Max(100_000) Integer count) {}

  // Used by PATCH — every field is optional. Constraints fire only when the value is present.
  record PatchRequest(
      @Size(min = 1, max = 1000) String title, @PositiveOrZero @Max(100_000) Integer count) {}

  // Outbound response shape. Decoupled from BookEntity so the API stays stable
  // even as the entity gains internal columns.
  record Response(Long id, String title, int count) {}

  // Root-key envelopes. Every request/response wraps its payload under "book" / "books"
  // so the wire format is self-describing and stays stable as we add sibling fields later.
  // @Valid on the inner field cascades Bean Validation into the wrapped DTO.
  record WriteEnvelope(@Valid @NotNull WriteRequest book) {}

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
