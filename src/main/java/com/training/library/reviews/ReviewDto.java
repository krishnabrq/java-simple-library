package com.training.library.reviews;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public interface ReviewDto {

  record WriteRequest(
      @NotNull Long bookId,
      @NotNull @Min(1) @Max(5) Short rating,
      @Size(max = 2000) String comment) {}

  record PatchRequest(@Min(1) @Max(5) Short rating, @Size(max = 2000) String comment) {}

  record Response(
      Long id,
      @JsonProperty("book_id") Long bookId,
      @JsonProperty("user_id") Long userId,
      short rating,
      String comment,
      @JsonProperty("created_at") Instant createdAt,
      @JsonProperty("updated_at") Instant updatedAt) {}

  record WriteEnvelope(@Valid @NotNull WriteRequest review) {}

  record PatchEnvelope(@Valid @NotNull PatchRequest review) {}

  record ResponseEnvelope(Response review) {}

  record ListEnvelope(List<Response> reviews, Meta meta) {}

  @JsonInclude(Include.NON_NULL)
  record Meta(
      long total,
      @JsonProperty("next_page") Integer nextPage,
      @JsonProperty("prev_page") Integer prevPage) {}

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  class Aggregate {
    @JsonProperty("book_id")
    private Long bookId;

    @JsonProperty("average_rating")
    private double averageRating;

    @JsonProperty("total_reviews")
    private long totalReviews;
  }

  record AggregateEnvelope(@JsonProperty("aggregate") Aggregate aggregate) {}
}
