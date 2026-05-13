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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

  @Transactional
  public BookLoanEntity returnLoan(Long userId, Long loanId) {
    UserEntity user = requireUser(userId);
    requireMember(user, "return");

    BookLoanEntity loan =
        loanRepository.findById(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));
    if (!loan.getUser().getId().equals(userId)) {
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

  public Page<BookLoanEntity> listMemberLoans(Long userId, int page, int limit) {
    UserEntity user = requireUser(userId);
    requireMember(user, "list loans");
    log.debug("listing loans for user={} page={} limit={}", userId, page, limit);
    return loanRepository.findAllByUserId(
        userId, PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "borrowedAt")));
  }

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
