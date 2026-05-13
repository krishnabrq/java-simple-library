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

  record WriteRequest(
      @NotBlank
          @Pattern(
              regexp = "^(\\d{9}[\\dX]|\\d{13})$",
              message = "must be a valid ISBN-10 or ISBN-13")
          String isbn,
      @NotBlank @Size(min = 1, max = 1000) String title,
      @NotNull @PositiveOrZero @Max(100_000) Integer count) {}

  record UpdateRequest(
      @NotBlank @Size(min = 1, max = 1000) String title,
      @NotNull @Positive @Max(100_000) Integer count) {}

  record PatchRequest(
      @Size(min = 1, max = 1000) String title, @Positive @Max(100_000) Integer count) {}

  record Response(
      Long id,
      String isbn,
      String title,
      int count,
      @JsonProperty("available_count") int availableCount,
      @JsonProperty("created_at") Instant createdAt,
      @JsonProperty("updated_at") Instant updatedAt) {}

  record WriteEnvelope(@Valid @NotNull WriteRequest book) {}

  record UpdateEnvelope(@Valid @NotNull UpdateRequest book) {}

  record PatchEnvelope(@Valid @NotNull PatchRequest book) {}

  record ResponseEnvelope(Response book) {}

  record ListEnvelope(List<Response> books, Meta meta) {}

  @JsonInclude(Include.NON_NULL)
  record Meta(
      long total,
      @JsonProperty("next_page") Integer nextPage,
      @JsonProperty("prev_page") Integer prevPage) {}
}
