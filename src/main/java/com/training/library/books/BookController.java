package com.training.library.books;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/books")
public class BookController {

  private final BookRepository repository;

  public BookController(BookRepository repository) {
    this.repository = repository;
  }

  @GetMapping
  public List<BookEntity> list() {
    return repository.findAll();
  }

  @GetMapping("/{bookId}")
  public BookEntity get(@PathVariable Long bookId) {
    return repository.findById(bookId)
        .orElseThrow(() -> new BookNotFoundException(bookId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BookEntity create(@RequestBody BookEntity book) {
    return repository.save(book);
  }

  @PutMapping("/{bookId}")
  public BookEntity replace(@PathVariable Long bookId, @RequestBody BookEntity update) {
    BookEntity existing = repository.findById(bookId)
        .orElseThrow(() -> new BookNotFoundException(bookId));
    existing.setTitle(update.getTitle());
    existing.setCount(update.getCount());
    return repository.save(existing);
  }

  @PatchMapping("/{bookId}")
  public BookEntity patch(@PathVariable Long bookId, @RequestBody BookDto.PatchRequest patch) {
    BookEntity existing = repository.findById(bookId)
        .orElseThrow(() -> new BookNotFoundException(bookId));
    if (patch.title() != null) {
      existing.setTitle(patch.title());
    }
    if (patch.count() != null) {
      existing.setCount(patch.count());
    }
    return repository.save(existing);
  }

  @DeleteMapping("/{bookId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long bookId) {
    if (!repository.existsById(bookId)) {
      throw new BookNotFoundException(bookId);
    }
    repository.deleteById(bookId);
  }
}
