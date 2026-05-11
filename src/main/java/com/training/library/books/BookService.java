package com.training.library.books;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookService {

  private static final Logger log = LoggerFactory.getLogger(BookService.class);

  private final BookRepository repository;
  private final BookMapper mapper;

  public BookService(BookRepository repository, BookMapper mapper) {
    this.repository = repository;
    this.mapper = mapper;
  }

  public List<BookEntity> findAll() {
    log.debug("listing all books");
    return repository.findAll();
  }

  public BookEntity findById(Long bookId) {
    log.debug("looking up book id={}", bookId);
    return findOrThrow(bookId);
  }

  @Transactional
  public BookEntity create(BookDto.WriteRequest request) {
    BookEntity saved = repository.save(mapper.toEntity(request));
    log.info("created book id={} title='{}'", saved.getId(), saved.getTitle());
    return saved;
  }

  @Transactional
  public BookEntity replace(Long bookId, BookDto.WriteRequest request) {
    BookEntity existing = findOrThrow(bookId);
    mapper.updateFromWriteRequest(existing, request);
    BookEntity saved = repository.save(existing);
    log.info("replaced book id={}", saved.getId());
    return saved;
  }

  @Transactional
  public BookEntity patch(Long bookId, BookDto.PatchRequest patch) {
    BookEntity existing = findOrThrow(bookId);
    mapper.updatePatch(existing, patch);
    BookEntity saved = repository.save(existing);
    log.info("patched book id={}", saved.getId());
    return saved;
  }

  @Transactional
  public void delete(Long bookId) {
    if (!repository.existsById(bookId)) {
      throw new BookNotFoundException(bookId);
    }
    repository.deleteById(bookId);
    log.info("deleted book id={}", bookId);
  }

  private BookEntity findOrThrow(Long bookId) {
    return repository.findById(bookId)
        .orElseThrow(() -> new BookNotFoundException(bookId));
  }
}
