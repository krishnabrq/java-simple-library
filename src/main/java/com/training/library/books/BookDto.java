package com.training.library.books;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public interface BookDto {

  // Used by POST (create) and PUT (full replace) — all fields required.
  record WriteRequest(
      @NotBlank @Size(min = 1, max = 200) String title,
      @NotNull @PositiveOrZero @Max(10_000) Integer count) {}

  // Used by PATCH — every field is optional. Constraints fire only when the value is present.
  record PatchRequest(
      @Size(min = 1, max = 200) String title,
      @PositiveOrZero @Max(10_000) Integer count) {}
}
