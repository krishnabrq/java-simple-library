package com.training.library.reviews;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<ReviewEntity, Long> {

  Page<ReviewEntity> findAllByBookId(Long bookId, Pageable pageable);

  boolean existsByBookIdAndUserId(Long bookId, Long userId);

  @Query(
      value =
          """
          SELECT
            COALESCE(AVG(rating)::float8, 0) AS average_rating,
            COUNT(*) AS total_reviews
          FROM reviews
          WHERE book_id = :bookId AND deleted_at IS NULL
          """,
      nativeQuery = true)
  ReviewAggregateProjection aggregateForBook(@Param("bookId") Long bookId);

  interface ReviewAggregateProjection {
    Double getAverageRating();

    Long getTotalReviews();
  }
}
