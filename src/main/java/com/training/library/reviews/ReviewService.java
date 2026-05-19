package com.training.library.reviews;

import com.training.library.auth.InvalidTokenException;
import com.training.library.books.BookEntity;
import com.training.library.books.BookNotFoundException;
import com.training.library.books.BookRepository;
import com.training.library.loans.BookLoanRepository;
import com.training.library.users.UserEntity;
import com.training.library.users.UserRepository;
import com.training.library.users.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReviewService {

  private final ReviewRepository reviewRepository;
  private final BookRepository bookRepository;
  private final UserRepository userRepository;
  private final BookLoanRepository loanRepository;

  public Page<ReviewEntity> listByBook(Long bookId, int page, int limit) {
    if (!bookRepository.existsById(bookId)) {
      throw new BookNotFoundException(bookId);
    }
    log.debug("listing reviews for book={} page={} limit={}", bookId, page, limit);
    return reviewRepository.findAllByBookId(
        bookId, PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt")));
  }

  public ReviewDto.Aggregate aggregateForBook(Long bookId) {
    if (!bookRepository.existsById(bookId)) {
      throw new BookNotFoundException(bookId);
    }
    ReviewRepository.ReviewAggregateProjection p = reviewRepository.aggregateForBook(bookId);
    return ReviewDto.Aggregate.builder()
        .bookId(bookId)
        .averageRating(p.getAverageRating() == null ? 0.0 : p.getAverageRating())
        .totalReviews(p.getTotalReviews() == null ? 0L : p.getTotalReviews())
        .build();
  }

  @Transactional
  public ReviewEntity create(Long userId, ReviewDto.WriteRequest request) {
    UserEntity user = requireUser(userId);
    requireMember(user, "post review");

    BookEntity book =
        bookRepository
            .findById(request.bookId())
            .orElseThrow(() -> new BookNotFoundException(request.bookId()));

    if (loanRepository.countByBookIdAndUserId(book.getId(), userId) == 0) {
      throw new ReviewNotPermittedException(
          "Only members who have borrowed book " + book.getId() + " can review it");
    }
    if (reviewRepository.existsByBookIdAndUserId(book.getId(), userId)) {
      throw new ReviewConflictException(
          "User " + userId + " has already reviewed book " + book.getId());
    }

    ReviewEntity entity =
        ReviewEntity.builder()
            .book(book)
            .user(user)
            .rating(request.rating())
            .comment(request.comment())
            .build();
    ReviewEntity saved = reviewRepository.save(entity);
    log.info("review created id={} book={} user={}", saved.getId(), book.getId(), userId);
    return saved;
  }

  @Transactional
  public ReviewEntity patch(Long userId, Long reviewId, ReviewDto.PatchRequest patch) {
    UserEntity user = requireUser(userId);
    requireMember(user, "edit review");

    ReviewEntity existing =
        reviewRepository
            .findById(reviewId)
            .orElseThrow(() -> new ReviewNotFoundException(reviewId));
    if (!existing.getUser().getId().equals(userId)) {
      throw new ReviewNotFoundException(reviewId);
    }
    if (patch.rating() != null) {
      existing.setRating(patch.rating());
    }
    if (patch.comment() != null) {
      existing.setComment(patch.comment());
    }
    ReviewEntity saved = reviewRepository.save(existing);
    log.info("review patched id={}", saved.getId());
    return saved;
  }

  @Transactional
  public void delete(Long userId, Long reviewId) {
    UserEntity user = requireUser(userId);
    ReviewEntity existing =
        reviewRepository
            .findById(reviewId)
            .orElseThrow(() -> new ReviewNotFoundException(reviewId));
    if (user.getRole() != UserRole.STAFF && !existing.getUser().getId().equals(userId)) {
      throw new ReviewNotFoundException(reviewId);
    }
    reviewRepository.deleteById(reviewId);
    log.info("review deleted id={} by user={}", reviewId, userId);
  }

  private UserEntity requireUser(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new InvalidTokenException("User no longer exists"));
  }

  private static void requireMember(UserEntity user, String action) {
    if (user.getRole() != UserRole.MEMBER) {
      throw new ReviewNotPermittedException(
          "Only members can " + action + " (role=" + user.getRole() + ")");
    }
  }
}
