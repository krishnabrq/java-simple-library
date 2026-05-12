package com.training.library.books;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
  @DisplayName("findAll - delegates to repository with 0-based page request")
  void findAll_returnsPage() {
    PageRequest expected = PageRequest.of(0, 10);
    Page<BookEntity> page = new PageImpl<>(List.of(mockBook), expected, 1);
    when(repository.findAll(expected)).thenReturn(page);

    Page<BookEntity> result = service.findAll(1, 10);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getTitle()).isEqualTo("Mock Title");
    assertThat(result.getTotalElements()).isEqualTo(1);
    verify(repository).findAll(expected);
  }

  @Test
  @DisplayName("findAll - converts 1-based API page to 0-based PageRequest")
  void findAll_convertsToZeroBased() {
    PageRequest expected = PageRequest.of(1, 10);
    when(repository.findAll(expected)).thenReturn(new PageImpl<>(List.of(), expected, 0));

    service.findAll(2, 10);

    verify(repository).findAll(expected);
  }

  @Test
  @DisplayName("findById - returns book when it exists")
  void findById_whenExists_returnsBook() {
    when(repository.findById(1L)).thenReturn(Optional.of(mockBook));

    BookEntity result = service.findById(1L);

    assertThat(result).isNotNull();
    assertThat(result.getTitle()).isEqualTo("Mock Title");
    verify(repository).findById(1L);
  }

  @Test
  @DisplayName("findById - throws BookNotFoundException when missing")
  void findById_whenMissing_throwsException() {
    when(repository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findById(1L))
        .isInstanceOf(BookNotFoundException.class)
        .hasMessageContaining("1");

    verify(repository).findById(1L);
  }

  @Test
  @DisplayName("create - maps request, saves entity, and returns it")
  void create_savesAndReturnsBook() {
    BookDto.WriteRequest request = new BookDto.WriteRequest("9780261103252", "New Title", 5);
    BookEntity mappedEntity = new BookEntity();
    mappedEntity.setIsbn("9780261103252");
    mappedEntity.setTitle("New Title");
    mappedEntity.setCount(5);

    when(mapper.toEntity(request)).thenReturn(mappedEntity);
    when(repository.save(mappedEntity)).thenReturn(mappedEntity);

    BookEntity result = service.create(request);

    assertThat(result).isNotNull();
    assertThat(result.getTitle()).isEqualTo("New Title");
    verify(mapper).toEntity(request);
    verify(repository).save(mappedEntity);
  }

  @Test
  @DisplayName("replace - throws when book missing")
  void replace_whenMissing_throwsException() {
    BookDto.UpdateRequest request = new BookDto.UpdateRequest("New Title", 5);
    when(repository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.replace(1L, request))
        .isInstanceOf(BookNotFoundException.class);
  }

  @Test
  @DisplayName("replace - updates existing entity and saves")
  void replace_updatesAndSaves() {
    BookDto.UpdateRequest request = new BookDto.UpdateRequest("New Title", 5);
    when(repository.findById(1L)).thenReturn(Optional.of(mockBook));
    when(repository.save(mockBook)).thenReturn(mockBook);

    BookEntity result = service.replace(1L, request);

    assertThat(result).isNotNull();
    verify(mapper).updateFromUpdateRequest(mockBook, request);
    verify(repository).save(mockBook);
  }

  @Test
  @DisplayName("patch - updates existing entity and saves")
  void patch_updatesAndSaves() {
    BookDto.PatchRequest request = new BookDto.PatchRequest(null, 15);
    when(repository.findById(1L)).thenReturn(Optional.of(mockBook));
    when(repository.save(mockBook)).thenReturn(mockBook);

    BookEntity result = service.patch(1L, request);

    assertThat(result).isNotNull();
    verify(mapper).updatePatch(mockBook, request);
    verify(repository).save(mockBook);
  }

  @Test
  @DisplayName("delete - throws when book missing")
  void delete_whenMissing_throwsException() {
    when(repository.existsById(1L)).thenReturn(false);

    assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BookNotFoundException.class);
  }

  @Test
  @DisplayName("delete - removes book when it exists")
  void delete_whenExists_deletesBook() {
    when(repository.existsById(1L)).thenReturn(true);
    doNothing().when(repository).deleteById(1L);

    service.delete(1L);

    verify(repository).existsById(1L);
    verify(repository).deleteById(1L);
  }
}
