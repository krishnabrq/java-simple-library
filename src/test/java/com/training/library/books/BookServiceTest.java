package com.training.library.books;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.training.library.loans.BookLoanRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

  @Mock private BookRepository repository;

  @Mock private BookLoanRepository loanRepository;

  @Mock private BookMapper mapper;

  @InjectMocks private BookService service;

  private BookEntity mockBook;

  @BeforeEach
  void setUp() {
    mockBook = new BookEntity();
    mockBook.setIsbn("9780261103252");
    mockBook.setTitle("Mock Title");
    mockBook.setCount(10);
  }

  @Test
  @DisplayName("findAll - delegates to findAllWithAvailability with 0-based page request")
  void findAll_returnsPage() {
    PageRequest expected = PageRequest.of(0, 10);
    BookView view = new BookView(mockBook, 0L);
    Page<BookView> page = new PageImpl<>(List.of(view), expected, 1);
    when(repository.findAllWithAvailability(expected)).thenReturn(page);

    Page<BookView> result = service.findAll(1, 10);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).book().getTitle()).isEqualTo("Mock Title");
    assertThat(result.getContent().get(0).activeLoanCount()).isZero();
    assertThat(result.getTotalElements()).isEqualTo(1);
    verify(repository).findAllWithAvailability(expected);
  }

  @Test
  @DisplayName("findAll - converts 1-based API page to 0-based PageRequest")
  void findAll_convertsToZeroBased() {
    PageRequest expected = PageRequest.of(1, 10);
    when(repository.findAllWithAvailability(expected))
        .thenReturn(new PageImpl<>(List.of(), expected, 0));

    service.findAll(2, 10);

    verify(repository).findAllWithAvailability(expected);
  }

  @Test
  @DisplayName("findById - returns view when book exists")
  void findById_whenExists_returnsView() {
    BookView view = new BookView(mockBook, 3L);
    when(repository.findByIdWithAvailability(1L)).thenReturn(Optional.of(view));

    BookView result = service.findById(1L);

    assertThat(result.book().getTitle()).isEqualTo("Mock Title");
    assertThat(result.activeLoanCount()).isEqualTo(3L);
    verify(repository).findByIdWithAvailability(1L);
  }

  @Test
  @DisplayName("findById - throws BookNotFoundException when missing")
  void findById_whenMissing_throwsException() {
    when(repository.findByIdWithAvailability(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findById(1L))
        .isInstanceOf(BookNotFoundException.class)
        .hasMessageContaining("1");

    verify(repository).findByIdWithAvailability(1L);
  }

  @Test
  @DisplayName("create - maps request, saves, returns view with 0 active loans")
  void create_savesAndReturnsView() {
    BookDto.WriteRequest request = new BookDto.WriteRequest("9780261103252", "New Title", 5);
    BookEntity mappedEntity = new BookEntity();
    mappedEntity.setIsbn("9780261103252");
    mappedEntity.setTitle("New Title");
    mappedEntity.setCount(5);

    when(mapper.toEntity(request)).thenReturn(mappedEntity);
    when(repository.save(mappedEntity)).thenReturn(mappedEntity);

    BookView result = service.create(request);

    assertThat(result.book().getTitle()).isEqualTo("New Title");
    assertThat(result.activeLoanCount()).isZero();
    verify(mapper).toEntity(request);
    verify(repository).save(mappedEntity);
    verify(loanRepository, never()).countByBookIdAndReturnedAtIsNull(1L);
  }

  @Test
  @DisplayName("replace - throws when book missing")
  void replace_whenMissing_throwsException() {
    BookDto.UpdateRequest request = new BookDto.UpdateRequest("New Title", 5);
    when(repository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.replace(1L, request))
        .isInstanceOf(BookNotFoundException.class);

    verify(loanRepository, never()).countByBookIdAndReturnedAtIsNull(1L);
  }

  @Test
  @DisplayName("replace - updates and returns view carrying the active loan count")
  void replace_updatesAndReturnsView() {
    BookDto.UpdateRequest request = new BookDto.UpdateRequest("New Title", 5);
    when(repository.findById(1L)).thenReturn(Optional.of(mockBook));
    when(loanRepository.countByBookIdAndReturnedAtIsNull(1L)).thenReturn(2L);
    when(repository.save(mockBook)).thenReturn(mockBook);

    BookView result = service.replace(1L, request);

    assertThat(result.book()).isSameAs(mockBook);
    assertThat(result.activeLoanCount()).isEqualTo(2L);
    verify(mapper).updateFromUpdateRequest(mockBook, request);
    verify(repository).save(mockBook);
  }

  @Test
  @DisplayName("replace - throws 409 when new count is below active loans")
  void replace_belowActiveLoans_throwsConflict() {
    BookDto.UpdateRequest request = new BookDto.UpdateRequest("New Title", 2);
    when(repository.findById(1L)).thenReturn(Optional.of(mockBook));
    when(loanRepository.countByBookIdAndReturnedAtIsNull(1L)).thenReturn(5L);

    assertThatThrownBy(() -> service.replace(1L, request))
        .isInstanceOf(BookConflictException.class)
        .hasMessageContaining("active loan");

    verify(repository, never()).save(mockBook);
  }

  @Test
  @DisplayName("patch - updates and returns view carrying the active loan count")
  void patch_updatesAndReturnsView() {
    BookDto.PatchRequest request = new BookDto.PatchRequest(null, 15);
    when(repository.findById(1L)).thenReturn(Optional.of(mockBook));
    when(loanRepository.countByBookIdAndReturnedAtIsNull(1L)).thenReturn(1L);
    when(repository.save(mockBook)).thenReturn(mockBook);

    BookView result = service.patch(1L, request);

    assertThat(result.book()).isSameAs(mockBook);
    assertThat(result.activeLoanCount()).isEqualTo(1L);
    verify(mapper).updatePatch(mockBook, request);
    verify(repository).save(mockBook);
  }

  @Test
  @DisplayName("patch - throws 409 when new count is below active loans")
  void patch_belowActiveLoans_throwsConflict() {
    BookDto.PatchRequest request = new BookDto.PatchRequest(null, 1);
    when(repository.findById(1L)).thenReturn(Optional.of(mockBook));
    when(loanRepository.countByBookIdAndReturnedAtIsNull(1L)).thenReturn(3L);

    assertThatThrownBy(() -> service.patch(1L, request))
        .isInstanceOf(BookConflictException.class)
        .hasMessageContaining("active loan");

    verify(repository, never()).save(mockBook);
  }

  @Test
  @DisplayName("patch - skips loan check when count is not in the request")
  void patch_titleOnly_skipsLoanCheck() {
    BookDto.PatchRequest request = new BookDto.PatchRequest("Just retitling", null);
    when(repository.findById(1L)).thenReturn(Optional.of(mockBook));
    when(loanRepository.countByBookIdAndReturnedAtIsNull(1L)).thenReturn(7L);
    when(repository.save(mockBook)).thenReturn(mockBook);

    BookView result = service.patch(1L, request);

    assertThat(result.activeLoanCount()).isEqualTo(7L);
    verify(repository).save(mockBook);
  }

  @Test
  @DisplayName("delete - throws when book missing")
  void delete_whenMissing_throwsException() {
    when(repository.existsById(1L)).thenReturn(false);

    assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BookNotFoundException.class);

    verify(loanRepository, never()).countByBookIdAndReturnedAtIsNull(1L);
    verify(repository, never()).deleteById(1L);
  }

  @Test
  @DisplayName("delete - throws 409 when active loans outstanding")
  void delete_withActiveLoans_throwsConflict() {
    when(repository.existsById(1L)).thenReturn(true);
    when(loanRepository.countByBookIdAndReturnedAtIsNull(1L)).thenReturn(2L);

    assertThatThrownBy(() -> service.delete(1L))
        .isInstanceOf(BookConflictException.class)
        .hasMessageContaining("active loan");

    verify(repository, never()).deleteById(1L);
  }

  @Test
  @DisplayName("delete - soft-deletes when no active loans exist")
  void delete_whenNoActiveLoans_softDeletes() {
    when(repository.existsById(1L)).thenReturn(true);
    when(loanRepository.countByBookIdAndReturnedAtIsNull(1L)).thenReturn(0L);

    service.delete(1L);

    verify(repository).deleteById(1L);
  }
}
