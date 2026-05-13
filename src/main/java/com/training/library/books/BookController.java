package com.training.library.books;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
  public BookDto.ListEnvelope list(
      @RequestParam(defaultValue = "1") @Min(1) Integer page,
      @RequestParam(defaultValue = "10") @Min(10) @Max(50) Integer limit) {
    Page<BookView> result = service.findAll(page, limit);
    Integer nextPage = result.hasNext() ? page + 1 : null;
    Integer prevPage = page > 1 ? page - 1 : null;
    BookDto.Meta meta = new BookDto.Meta(result.getTotalElements(), nextPage, prevPage);
    return new BookDto.ListEnvelope(mapper.toResponses(result.getContent()), meta);
  }

  @GetMapping("/{bookId}")
  public BookDto.ResponseEnvelope get(@PathVariable @Min(1) Long bookId) {
    return new BookDto.ResponseEnvelope(mapper.toResponse(service.findById(bookId)));
  }

  @PostMapping
  @PreAuthorize("hasRole('STAFF')")
  @ResponseStatus(HttpStatus.CREATED)
  public BookDto.ResponseEnvelope create(@Valid @RequestBody BookDto.WriteEnvelope envelope) {
    return new BookDto.ResponseEnvelope(mapper.toResponse(service.create(envelope.book())));
  }

  @PutMapping("/{bookId}")
  @PreAuthorize("hasRole('STAFF')")
  public BookDto.ResponseEnvelope replace(
      @PathVariable @Min(1) Long bookId, @Valid @RequestBody BookDto.UpdateEnvelope envelope) {
    return new BookDto.ResponseEnvelope(
        mapper.toResponse(service.replace(bookId, envelope.book())));
  }

  @PatchMapping("/{bookId}")
  @PreAuthorize("hasRole('STAFF')")
  public BookDto.ResponseEnvelope patch(
      @PathVariable @Min(1) Long bookId, @Valid @RequestBody BookDto.PatchEnvelope envelope) {
    return new BookDto.ResponseEnvelope(mapper.toResponse(service.patch(bookId, envelope.book())));
  }

  @DeleteMapping("/{bookId}")
  @PreAuthorize("hasRole('STAFF')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable @Min(1) Long bookId) {
    service.delete(bookId);
  }
}
