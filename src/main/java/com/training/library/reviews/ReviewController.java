package com.training.library.reviews;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

  private final ReviewService service;
  private final ReviewMapper mapper;

  @GetMapping
  public ReviewDto.ListEnvelope listByBook(
      @RequestParam @Min(1) Long bookId,
      @RequestParam(defaultValue = "1") @Min(1) Integer page,
      @RequestParam(defaultValue = "10") @Min(10) @Max(50) Integer limit) {
    Page<ReviewEntity> result = service.listByBook(bookId, page, limit);
    Integer nextPage = result.hasNext() ? page + 1 : null;
    Integer prevPage = page > 1 ? page - 1 : null;
    ReviewDto.Meta meta = new ReviewDto.Meta(result.getTotalElements(), nextPage, prevPage);
    return new ReviewDto.ListEnvelope(mapper.toResponses(result.getContent()), meta);
  }

  @GetMapping("/aggregate")
  public ReviewDto.AggregateEnvelope aggregateForBook(@RequestParam @Min(1) Long bookId) {
    return new ReviewDto.AggregateEnvelope(service.aggregateForBook(bookId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ReviewDto.ResponseEnvelope create(
      @Valid @RequestBody ReviewDto.WriteEnvelope envelope, @AuthenticationPrincipal Jwt jwt) {
    ReviewEntity created = service.create(subjectAsUserId(jwt), envelope.review());
    return new ReviewDto.ResponseEnvelope(mapper.toResponse(created));
  }

  @PatchMapping("/{reviewId}")
  public ReviewDto.ResponseEnvelope patch(
      @PathVariable @Min(1) Long reviewId,
      @Valid @RequestBody ReviewDto.PatchEnvelope envelope,
      @AuthenticationPrincipal Jwt jwt) {
    ReviewEntity patched = service.patch(subjectAsUserId(jwt), reviewId, envelope.review());
    return new ReviewDto.ResponseEnvelope(mapper.toResponse(patched));
  }

  @DeleteMapping("/{reviewId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable @Min(1) Long reviewId, @AuthenticationPrincipal Jwt jwt) {
    service.delete(subjectAsUserId(jwt), reviewId);
  }

  private static Long subjectAsUserId(Jwt jwt) {
    return Long.valueOf(jwt.getSubject());
  }
}
