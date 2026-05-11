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

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/books")
public class BookController {

  private final BookService service;
  private final BookMapper mapper;

  public BookController(BookService service, BookMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping
  public List<BookDto.Response> list() {
    return mapper.toResponses(service.findAll());
  }

  @GetMapping("/{bookId}")
  public BookDto.Response get(@PathVariable @Min(1) Long bookId) {
    return mapper.toResponse(service.findById(bookId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BookDto.Response create(@Valid @RequestBody BookDto.WriteRequest request) {
    return mapper.toResponse(service.create(request));
  }

  @PutMapping("/{bookId}")
  public BookDto.Response replace(
      @PathVariable @Min(1) Long bookId,
      @Valid @RequestBody BookDto.WriteRequest request) {
    return mapper.toResponse(service.replace(bookId, request));
  }

  @PatchMapping("/{bookId}")
  public BookDto.Response patch(
      @PathVariable @Min(1) Long bookId,
      @Valid @RequestBody BookDto.PatchRequest patch) {
    return mapper.toResponse(service.patch(bookId, patch));
  }

  @DeleteMapping("/{bookId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable @Min(1) Long bookId) {
    service.delete(bookId);
  }
}
