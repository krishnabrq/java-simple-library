package com.training.library.books;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookService {

  private final BookRepository repository;
  private final BookMapper mapper;

  public BookService(BookRepository repository, BookMapper mapper) {
    this.repository = repository;
    this.mapper = mapper;
  }

  public List<BookEntity> findAll() {
    return repository.findAll();
  }

  public BookEntity findById(Long bookId) {
    return findOrThrow(bookId);
  }

  @Transactional
  public BookEntity create(BookDto.WriteRequest request) {
    return repository.save(mapper.toEntity(request));
  }

  @Transactional
  public BookEntity replace(Long bookId, BookDto.WriteRequest request) {
    BookEntity existing = findOrThrow(bookId);
    mapper.updateFromWriteRequest(existing, request);
    return repository.save(existing);
  }

  @Transactional
  public BookEntity patch(Long bookId, BookDto.PatchRequest patch) {
    BookEntity existing = findOrThrow(bookId);
    mapper.updatePatch(existing, patch);
    return repository.save(existing);
  }

  @Transactional
  public void delete(Long bookId) {
    if (!repository.existsById(bookId)) {
      throw new BookNotFoundException(bookId);
    }
    repository.deleteById(bookId);
  }

  private BookEntity findOrThrow(Long bookId) {
    return repository.findById(bookId)
        .orElseThrow(() -> new BookNotFoundException(bookId));
  }
}
