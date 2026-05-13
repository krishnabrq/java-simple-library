package com.training.library.loans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.training.library.auth.InvalidTokenException;
import com.training.library.books.BookEntity;
import com.training.library.books.BookNotFoundException;
import com.training.library.books.BookRepository;
import com.training.library.users.UserEntity;
import com.training.library.users.UserRepository;
import com.training.library.users.UserRole;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

  @Mock private BookLoanRepository loanRepository;

  @Mock private BookRepository bookRepository;

  @Mock private UserRepository userRepository;

  @InjectMocks private LoanService service;

  private static UserEntity user(Long id, UserRole role) {
    UserEntity u = new UserEntity();
    u.setName("Test");
    u.setEmail("u@e.test");
    u.setPasswordHash("h");
    u.setRole(role);
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  private static BookEntity book(Long id, int count) {
    BookEntity b = new BookEntity();
    b.setIsbn("9780000000001");
    b.setTitle("A Book");
    b.setCount(count);
    ReflectionTestUtils.setField(b, "id", id);
    return b;
  }

  private static BookLoanEntity loan(
      Long id, BookEntity book, UserEntity user, Instant returnedAt) {
    BookLoanEntity l = new BookLoanEntity();
    l.setBook(book);
    l.setUser(user);
    l.setBorrowedAt(Instant.now().minusSeconds(60));
    l.setReturnedAt(returnedAt);
    ReflectionTestUtils.setField(l, "id", id);
    return l;
  }

  @Test
  @DisplayName("borrow - creates loan when a copy is available")
  void borrow_happyPath_createsLoan() {
    UserEntity u = user(1L, UserRole.MEMBER);
    BookEntity b = book(10L, 3);

    when(userRepository.findById(1L)).thenReturn(Optional.of(u));
    when(bookRepository.findById(10L)).thenReturn(Optional.of(b));
    when(loanRepository.countByBookIdAndReturnedAtIsNull(10L)).thenReturn(2L);
    when(loanRepository.save(any(BookLoanEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    BookLoanEntity result = service.borrow(1L, 10L);

    assertThat(result.getBook()).isSameAs(b);
    assertThat(result.getUser()).isSameAs(u);
    assertThat(result.getBorrowedAt()).isNotNull();
    assertThat(result.getReturnedAt()).isNull();
  }

  @Test
  @DisplayName("borrow - 401 when the JWT subject points at a user that no longer exists")
  void borrow_missingUser_throwsInvalidToken() {
    when(userRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.borrow(1L, 10L)).isInstanceOf(InvalidTokenException.class);

    verify(bookRepository, never()).findById(any());
  }

  @Test
  @DisplayName("borrow - 403 when caller is STAFF (only members can borrow)")
  void borrow_staffRole_throwsForbidden() {
    UserEntity u = user(1L, UserRole.STAFF);
    when(userRepository.findById(1L)).thenReturn(Optional.of(u));

    assertThatThrownBy(() -> service.borrow(1L, 10L))
        .isInstanceOf(LoanNotPermittedException.class)
        .hasMessageContaining("borrow");

    verify(bookRepository, never()).findById(any());
    verify(loanRepository, never()).save(any());
  }

  @Test
  @DisplayName("borrow - 404 when book id is unknown")
  void borrow_unknownBook_throwsBookNotFound() {
    UserEntity u = user(1L, UserRole.MEMBER);
    when(userRepository.findById(1L)).thenReturn(Optional.of(u));
    when(bookRepository.findById(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.borrow(1L, 10L)).isInstanceOf(BookNotFoundException.class);

    verify(loanRepository, never()).save(any());
  }

  @Test
  @DisplayName("borrow - 409 when no copies are available")
  void borrow_noCopies_throwsConflict() {
    UserEntity u = user(1L, UserRole.MEMBER);
    BookEntity b = book(10L, 2);
    when(userRepository.findById(1L)).thenReturn(Optional.of(u));
    when(bookRepository.findById(10L)).thenReturn(Optional.of(b));
    when(loanRepository.countByBookIdAndReturnedAtIsNull(10L)).thenReturn(2L);

    assertThatThrownBy(() -> service.borrow(1L, 10L))
        .isInstanceOf(LoanConflictException.class)
        .hasMessageContaining("No copies");

    verify(loanRepository, never()).save(any());
  }

  @Test
  @DisplayName("return - sets returned_at and saves")
  void returnLoan_happyPath_setsReturnedAt() {
    UserEntity u = user(1L, UserRole.MEMBER);
    BookLoanEntity l = loan(7L, book(10L, 3), u, null);

    when(userRepository.findById(1L)).thenReturn(Optional.of(u));
    when(loanRepository.findById(7L)).thenReturn(Optional.of(l));
    when(loanRepository.save(l)).thenReturn(l);

    BookLoanEntity result = service.returnLoan(1L, 7L);

    assertThat(result.getReturnedAt()).isNotNull();
    verify(loanRepository).save(l);
  }

  @Test
  @DisplayName("return - 404 when the loan belongs to someone else")
  void returnLoan_otherUsersLoan_throwsNotFound() {
    UserEntity caller = user(1L, UserRole.MEMBER);
    UserEntity owner = user(2L, UserRole.MEMBER);
    BookLoanEntity l = loan(7L, book(10L, 3), owner, null);

    when(userRepository.findById(1L)).thenReturn(Optional.of(caller));
    when(loanRepository.findById(7L)).thenReturn(Optional.of(l));

    assertThatThrownBy(() -> service.returnLoan(1L, 7L)).isInstanceOf(LoanNotFoundException.class);

    verify(loanRepository, never()).save(any());
  }

  @Test
  @DisplayName("return - 409 when the loan is already returned")
  void returnLoan_alreadyReturned_throwsConflict() {
    UserEntity u = user(1L, UserRole.MEMBER);
    BookLoanEntity l = loan(7L, book(10L, 3), u, Instant.now().minusSeconds(30));

    when(userRepository.findById(1L)).thenReturn(Optional.of(u));
    when(loanRepository.findById(7L)).thenReturn(Optional.of(l));

    assertThatThrownBy(() -> service.returnLoan(1L, 7L))
        .isInstanceOf(LoanConflictException.class)
        .hasMessageContaining("already returned");

    verify(loanRepository, never()).save(any());
  }

  @Test
  @DisplayName("return - 403 when caller is STAFF")
  void returnLoan_staffRole_throwsForbidden() {
    UserEntity u = user(1L, UserRole.STAFF);
    when(userRepository.findById(1L)).thenReturn(Optional.of(u));

    assertThatThrownBy(() -> service.returnLoan(1L, 7L))
        .isInstanceOf(LoanNotPermittedException.class)
        .hasMessageContaining("return");

    verify(loanRepository, never()).findById(any());
  }

  @Test
  @DisplayName("return - 404 when loan id is unknown")
  void returnLoan_unknownLoan_throwsNotFound() {
    UserEntity u = user(1L, UserRole.MEMBER);
    when(userRepository.findById(1L)).thenReturn(Optional.of(u));
    when(loanRepository.findById(7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.returnLoan(1L, 7L)).isInstanceOf(LoanNotFoundException.class);
  }
}
