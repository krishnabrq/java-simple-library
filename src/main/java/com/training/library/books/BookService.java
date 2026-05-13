package com.training.library.books;

import com.training.library.loans.BookLoanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookService {

  private static final Logger log = LoggerFactory.getLogger(BookService.class);

  private final BookRepository repository;
  private final BookLoanRepository loanRepository;
  private final BookMapper mapper;

  public BookService(
      BookRepository repository, BookLoanRepository loanRepository, BookMapper mapper) {
    this.repository = repository;
    this.loanRepository = loanRepository;
    this.mapper = mapper;
  }

  // Page is 1-based at the API edge; Spring Data is 0-based internally, so subtract here.
  // findAllWithAvailability fuses books + active-loan count into one SQL round trip.
  public Page<BookView> findAll(int page, int limit) {
    log.debug("listing books page={} limit={}", page, limit);
    return repository.findAllWithAvailability(PageRequest.of(page - 1, limit));
  }

  public BookView findById(Long bookId) {
    log.debug("looking up book id={}", bookId);
    return repository
        .findByIdWithAvailability(bookId)
        .orElseThrow(() -> new BookNotFoundException(bookId));
  }

  @Transactional
  public BookView create(BookDto.WriteRequest request) {
    BookEntity saved = repository.save(mapper.toEntity(request));
    log.info("created book id={} title='{}'", saved.getId(), saved.getTitle());
    // A new book can't have any loans yet — skip the count query.
    return new BookView(saved, 0L);
  }

  @Transactional
  public BookView replace(Long bookId, BookDto.UpdateRequest request) {
    BookEntity existing = findOrThrow(bookId);
    long active = loanRepository.countByBookIdAndReturnedAtIsNull(bookId);
    requireCountAboveActiveLoans(bookId, request.count(), active);
    mapper.updateFromUpdateRequest(existing, request);
    BookEntity saved = repository.save(existing);
    log.info("replaced book id={}", saved.getId());
    return new BookView(saved, active);
  }

  @Transactional
  public BookView patch(Long bookId, BookDto.PatchRequest patch) {
    BookEntity existing = findOrThrow(bookId);
    long active = loanRepository.countByBookIdAndReturnedAtIsNull(bookId);
    if (patch.count() != null) {
      requireCountAboveActiveLoans(bookId, patch.count(), active);
    }
    mapper.updatePatch(existing, patch);
    BookEntity saved = repository.save(existing);
    log.info("patched book id={}", saved.getId());
    return new BookView(saved, active);
  }

  @Transactional
  public void delete(Long bookId) {
    if (!repository.existsById(bookId)) {
      throw new BookNotFoundException(bookId);
    }
    long active = loanRepository.countByBookIdAndReturnedAtIsNull(bookId);
    if (active > 0) {
      throw new BookConflictException(
          "Cannot delete book " + bookId + ": " + active + " active loan(s) outstanding");
    }
    // Soft delete: BookEntity's @SQLDelete rewrites this to UPDATE deleted_at.
    repository.deleteById(bookId);
    log.info("deleted book id={}", bookId);
  }

  private BookEntity findOrThrow(Long bookId) {
    return repository.findById(bookId).orElseThrow(() -> new BookNotFoundException(bookId));
  }

  private static void requireCountAboveActiveLoans(Long bookId, int newCount, long active) {
    if (newCount < active) {
      throw new BookConflictException(
          "Cannot set book "
              + bookId
              + " count to "
              + newCount
              + ": "
              + active
              + " active loan(s) outstanding");
    }
  }
}
