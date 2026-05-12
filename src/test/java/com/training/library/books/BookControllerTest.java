package com.training.library.books;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class BookControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private BookRepository bookRepository;

  @BeforeEach
  void setUp() {
    bookRepository.deleteAll();
  }

  private BookEntity book(String isbn, String title, int count) {
    BookEntity b = new BookEntity();
    b.setIsbn(isbn);
    b.setTitle(title);
    b.setCount(count);
    return b;
  }

  @Test
  @DisplayName("GET /api/v1/books - returns empty list and zeroed meta when no books exist")
  void getBooks_whenEmpty_returnsEmptyArray() throws Exception {
    mockMvc
        .perform(get("/api/v1/books"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.books").isArray())
        .andExpect(jsonPath("$.books").isEmpty())
        .andExpect(jsonPath("$.meta.total").value(0))
        .andExpect(jsonPath("$.meta.next_page").doesNotExist())
        .andExpect(jsonPath("$.meta.prev_page").doesNotExist());
  }

  @Test
  @DisplayName("GET /api/v1/books - returns first page with default page=1 limit=10")
  void getBooks_whenPopulated_returnsBooks() throws Exception {
    bookRepository.save(book("9780000000001", "Book 1", 10));
    bookRepository.save(book("9780000000002", "Book 2", 5));

    mockMvc
        .perform(get("/api/v1/books"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.books.length()").value(2))
        .andExpect(jsonPath("$.books[0].title").value("Book 1"))
        .andExpect(jsonPath("$.books[0].isbn").value("9780000000001"))
        .andExpect(jsonPath("$.books[0].created_at").exists())
        .andExpect(jsonPath("$.books[0].updated_at").exists())
        .andExpect(jsonPath("$.books[1].title").value("Book 2"))
        .andExpect(jsonPath("$.meta.total").value(2));
  }

  @Test
  @DisplayName("GET /api/v1/books - middle page reports both next_page and prev_page")
  void getBooks_middlePage_reportsBothNeighbours() throws Exception {
    List<BookEntity> saved = new ArrayList<>();
    for (int i = 1; i <= 25; i++) {
      saved.add(bookRepository.save(book(isbnSeed(i), "Book " + i, i)));
    }

    mockMvc
        .perform(get("/api/v1/books").param("page", "2").param("limit", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.books.length()").value(10))
        .andExpect(jsonPath("$.books[0].title").value("Book 11"))
        .andExpect(jsonPath("$.books[9].title").value("Book 20"))
        .andExpect(jsonPath("$.meta.total").value(25))
        .andExpect(jsonPath("$.meta.next_page").value(3))
        .andExpect(jsonPath("$.meta.prev_page").value(1));
  }

  @Test
  @DisplayName("GET /api/v1/books - last page has prev_page but no next_page")
  void getBooks_lastPage_noNextPage() throws Exception {
    for (int i = 1; i <= 25; i++) {
      bookRepository.save(book(isbnSeed(i), "Book " + i, i));
    }

    mockMvc
        .perform(get("/api/v1/books").param("page", "3").param("limit", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.books.length()").value(5))
        .andExpect(jsonPath("$.meta.total").value(25))
        .andExpect(jsonPath("$.meta.next_page").doesNotExist())
        .andExpect(jsonPath("$.meta.prev_page").value(2));
  }

  @Test
  @DisplayName("GET /api/v1/books - returns 400 when page < 1")
  void getBooks_pageBelowMin_returns400() throws Exception {
    mockMvc.perform(get("/api/v1/books").param("page", "0")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /api/v1/books - returns 400 when limit < 10")
  void getBooks_limitBelowMin_returns400() throws Exception {
    mockMvc.perform(get("/api/v1/books").param("limit", "5")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /api/v1/books - returns 400 when limit > 50")
  void getBooks_limitAboveMax_returns400() throws Exception {
    mockMvc.perform(get("/api/v1/books").param("limit", "51")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /api/v1/books/{id} - returns book under 'book' root when exists")
  void getBook_whenExists_returnsBook() throws Exception {
    BookEntity saved = bookRepository.save(book("9780261103252", "The Hobbit", 10));

    mockMvc
        .perform(get("/api/v1/books/" + saved.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.book.id").value(saved.getId()))
        .andExpect(jsonPath("$.book.isbn").value("9780261103252"))
        .andExpect(jsonPath("$.book.title").value("The Hobbit"))
        .andExpect(jsonPath("$.book.count").value(10))
        .andExpect(jsonPath("$.book.created_at").exists())
        .andExpect(jsonPath("$.book.updated_at").exists());
  }

  @Test
  @DisplayName("GET /api/v1/books/{id} - returns 404 when book does not exist")
  void getBook_whenDoesNotExist_returns404() throws Exception {
    mockMvc.perform(get("/api/v1/books/999")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("POST /api/v1/books - creates book from {book:{...}} envelope and returns 201")
  void createBook_withValidRequest_returns201AndSavesToDb() throws Exception {
    BookDto.WriteEnvelope envelope =
        new BookDto.WriteEnvelope(new BookDto.WriteRequest("9780261103252", "The Hobbit", 10));

    mockMvc
        .perform(
            post("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(envelope)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.book.id").isNumber())
        .andExpect(jsonPath("$.book.isbn").value("9780261103252"))
        .andExpect(jsonPath("$.book.title").value("The Hobbit"))
        .andExpect(jsonPath("$.book.count").value(10));

    assertThat(bookRepository.findAll()).hasSize(1);
    BookEntity savedEntity = bookRepository.findAll().get(0);
    assertThat(savedEntity.getIsbn()).isEqualTo("9780261103252");
    assertThat(savedEntity.getTitle()).isEqualTo("The Hobbit");
    assertThat(savedEntity.getCount()).isEqualTo(10);
    assertThat(savedEntity.getCreatedAt()).isNotNull();
    assertThat(savedEntity.getUpdatedAt()).isNotNull();
    assertThat(savedEntity.getDeletedAt()).isNull();
  }

  @Test
  @DisplayName("POST /api/v1/books - returns 400 when title is blank")
  void createBook_withInvalidTitle_returns400() throws Exception {
    BookDto.WriteEnvelope envelope =
        new BookDto.WriteEnvelope(new BookDto.WriteRequest("9780261103252", "", 5));

    mockMvc
        .perform(
            post("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(envelope)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));

    assertThat(bookRepository.findAll()).isEmpty();
  }

  @Test
  @DisplayName("POST /api/v1/books - returns 400 when isbn is missing")
  void createBook_missingIsbn_returns400() throws Exception {
    BookDto.WriteEnvelope envelope =
        new BookDto.WriteEnvelope(new BookDto.WriteRequest(null, "The Hobbit", 10));

    mockMvc
        .perform(
            post("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(envelope)))
        .andExpect(status().isBadRequest());

    assertThat(bookRepository.findAll()).isEmpty();
  }

  @Test
  @DisplayName("POST /api/v1/books - returns 400 when isbn has wrong shape")
  void createBook_malformedIsbn_returns400() throws Exception {
    BookDto.WriteEnvelope envelope =
        new BookDto.WriteEnvelope(new BookDto.WriteRequest("not-an-isbn", "The Hobbit", 10));

    mockMvc
        .perform(
            post("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(envelope)))
        .andExpect(status().isBadRequest());

    assertThat(bookRepository.findAll()).isEmpty();
  }

  @Test
  @DisplayName("POST /api/v1/books - returns 400 when 'book' root key is missing")
  void createBook_missingRoot_returns400() throws Exception {
    mockMvc
        .perform(post("/api/v1/books").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());

    assertThat(bookRepository.findAll()).isEmpty();
  }

  @Test
  @DisplayName("PUT /api/v1/books/{id} - replaces title/count and leaves isbn unchanged")
  void replaceBook_withValidRequest_returns200() throws Exception {
    BookEntity saved = bookRepository.save(book("9780261103252", "Old Title", 5));

    BookDto.UpdateEnvelope envelope =
        new BookDto.UpdateEnvelope(new BookDto.UpdateRequest("New Title", 20));

    mockMvc
        .perform(
            put("/api/v1/books/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(envelope)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.book.isbn").value("9780261103252"))
        .andExpect(jsonPath("$.book.title").value("New Title"))
        .andExpect(jsonPath("$.book.count").value(20));

    BookEntity updated = bookRepository.findById(saved.getId()).orElseThrow();
    assertThat(updated.getIsbn()).isEqualTo("9780261103252");
    assertThat(updated.getTitle()).isEqualTo("New Title");
    assertThat(updated.getCount()).isEqualTo(20);
  }

  @Test
  @DisplayName("PUT /api/v1/books/{id} - returns 404 if book missing")
  void replaceBook_whenMissing_returns404() throws Exception {
    BookDto.UpdateEnvelope envelope =
        new BookDto.UpdateEnvelope(new BookDto.UpdateRequest("New Title", 20));

    mockMvc
        .perform(
            put("/api/v1/books/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(envelope)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("PATCH /api/v1/books/{id} - updates only provided fields via envelope")
  void patchBook_withValidRequest_returns200() throws Exception {
    BookEntity saved = bookRepository.save(book("9780261103252", "Original Title", 5));

    BookDto.PatchEnvelope envelope = new BookDto.PatchEnvelope(new BookDto.PatchRequest(null, 15));

    mockMvc
        .perform(
            patch("/api/v1/books/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(envelope)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.book.title").value("Original Title"))
        .andExpect(jsonPath("$.book.count").value(15));

    BookEntity updated = bookRepository.findById(saved.getId()).orElseThrow();
    assertThat(updated.getTitle()).isEqualTo("Original Title");
    assertThat(updated.getCount()).isEqualTo(15);
  }

  @Test
  @DisplayName("DELETE /api/v1/books/{id} - soft-deletes book; subsequent GET returns 404")
  void deleteBook_whenExists_softDeletesAndReturns204() throws Exception {
    BookEntity saved = bookRepository.save(book("9780261103252", "To be deleted", 5));

    mockMvc.perform(delete("/api/v1/books/" + saved.getId())).andExpect(status().isNoContent());

    // findById goes through @SQLRestriction("deleted_at IS NULL") — soft-deleted row is invisible.
    assertThat(bookRepository.findById(saved.getId())).isEmpty();

    mockMvc.perform(get("/api/v1/books/" + saved.getId())).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("DELETE /api/v1/books/{id} - returns 404 when book missing")
  void deleteBook_whenMissing_returns404() throws Exception {
    mockMvc.perform(delete("/api/v1/books/999")).andExpect(status().isNotFound());
  }

  // 13-digit ISBN seed (978 + 9-digit zero-padded counter + check-digit placeholder '0').
  private static String isbnSeed(int n) {
    return String.format("978%010d", n);
  }
}
