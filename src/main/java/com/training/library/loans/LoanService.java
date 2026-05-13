package com.training.library.loans;

import com.training.library.auth.InvalidTokenException;
import com.training.library.books.BookEntity;
import com.training.library.books.BookNotFoundException;
import com.training.library.books.BookRepository;
import com.training.library.users.UserEntity;
import com.training.library.users.UserRepository;
import com.training.library.users.UserRole;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LoanService {

  private static final Logger log = LoggerFactory.getLogger(LoanService.class);

  private final BookLoanRepository loanRepository;
  private final BookRepository bookRepository;
  private final UserRepository userRepository;

  public LoanService(
      BookLoanRepository loanRepository,
      BookRepository bookRepository,
      UserRepository userRepository) {
    this.loanRepository = loanRepository;
    this.bookRepository = bookRepository;
    this.userRepository = userRepository;
  }

  // Borrow flow:
  //   1. Resolve the caller from the live DB (rejects users that were deleted after their
  //      JWT was issued).
  //   2. Enforce role at the service layer — STAFF cannot borrow. Spring Security's
  //      filter chain already gates the route to "authenticated", but the borrowing rule
  //      is a domain rule and lives here.
  //   3. Load the book, check there's at least one copy not currently on loan.
  //   4. Insert the loan with borrowed_at = NOW(), returned_at = NULL.
  // Race note: the count check is not atomic with the insert — two concurrent borrows can
  // each pass the check and both create loans. Acceptable for this learning project; the
  // real fix is a pessimistic lock or a count-based unique constraint.
  @Transactional
  public BookLoanEntity borrow(Long userId, Long bookId) {
    UserEntity user = requireUser(userId);
    requireMember(user, "borrow");

    BookEntity book =
        bookRepository.findById(bookId).orElseThrow(() -> new BookNotFoundException(bookId));
    long active = loanRepository.countByBookIdAndReturnedAtIsNull(bookId);
    if (active >= book.getCount()) {
      throw new LoanConflictException(
          "No copies available for book " + bookId + " (count=" + book.getCount() + ")");
    }

    BookLoanEntity loan = new BookLoanEntity();
    loan.setBook(book);
    loan.setUser(user);
    loan.setBorrowedAt(Instant.now());
    BookLoanEntity saved = loanRepository.save(loan);
    log.info("loan created id={} book={} user={}", saved.getId(), bookId, userId);
    return saved;
  }

  // Return flow:
  //   1. Resolve caller; enforce role.
  //   2. Load the loan. If it doesn't exist OR belongs to a different user, we throw 404
  //      — refusing to leak the existence of other members' loans.
  //   3. Reject double-return (returned_at already set).
  //   4. Set returned_at = NOW(), persist.
  @Transactional
  public BookLoanEntity returnLoan(Long userId, Long loanId) {
    UserEntity user = requireUser(userId);
    requireMember(user, "return");

    BookLoanEntity loan =
        loanRepository.findById(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));
    if (!loan.getUser().getId().equals(userId)) {
      // Same 404 as the missing case — don't disclose loans of other members.
      throw new LoanNotFoundException(loanId);
    }
    if (loan.getReturnedAt() != null) {
      throw new LoanConflictException("Loan " + loanId + " was already returned");
    }
    loan.setReturnedAt(Instant.now());
    BookLoanEntity saved = loanRepository.save(loan);
    log.info("loan returned id={} user={}", saved.getId(), userId);
    return saved;
  }

  // The JWT carries a user id in `sub`. The user may have been deleted between token
  // issuance and now — translate the absence into an InvalidTokenException so the existing
  // 401 handler maps it consistently with refresh-against-missing-user.
  private UserEntity requireUser(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new InvalidTokenException("User no longer exists"));
  }

  private static void requireMember(UserEntity user, String action) {
    if (user.getRole() != UserRole.MEMBER) {
      throw new LoanNotPermittedException(
          "Only members can " + action + " books (role=" + user.getRole() + ")");
    }
  }
}
