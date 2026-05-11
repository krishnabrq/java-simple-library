package com.training.library.books;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController()
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

  @PostMapping
  public BookEntity create(@RequestBody BookEntity book) {
    return repository.save(book);
  }

}
