package com.training.library.books;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

  @Test
  @DisplayName("GET /api/v1/books - returns empty list when no books exist")
  void getBooks_whenEmpty_returnsEmptyArray() throws Exception {
    mockMvc
        .perform(get("/api/v1/books"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  @DisplayName("GET /api/v1/books - returns all books")
  void getBooks_whenPopulated_returnsBooks() throws Exception {
    BookEntity book1 = new BookEntity();
    book1.setTitle("Book 1");
    book1.setCount(10);
    bookRepository.save(book1);

    BookEntity book2 = new BookEntity();
    book2.setTitle("Book 2");
    book2.setCount(5);
    bookRepository.save(book2);

    mockMvc
        .perform(get("/api/v1/books"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].title").value("Book 1"))
        .andExpect(jsonPath("$[1].title").value("Book 2"));
  }

  @Test
  @DisplayName("GET /api/v1/books/{id} - returns book when exists")
  void getBook_whenExists_returnsBook() throws Exception {
    BookEntity book = new BookEntity();
    book.setTitle("The Hobbit");
    book.setCount(10);
    BookEntity saved = bookRepository.save(book);

    mockMvc
        .perform(get("/api/v1/books/" + saved.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(saved.getId()))
        .andExpect(jsonPath("$.title").value("The Hobbit"));
  }

  @Test
  @DisplayName("GET /api/v1/books/{id} - returns 404 when book does not exist")
  void getBook_whenDoesNotExist_returns404() throws Exception {
    mockMvc.perform(get("/api/v1/books/999")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("POST /api/v1/books - creates book and returns 201")
  void createBook_withValidRequest_returns201AndSavesToDb() throws Exception {
    BookDto.WriteRequest request = new BookDto.WriteRequest("The Hobbit", 10);

    mockMvc
        .perform(
            post("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.title").value("The Hobbit"))
        .andExpect(jsonPath("$.count").value(10));

    assertThat(bookRepository.findAll()).hasSize(1);
    BookEntity savedEntity = bookRepository.findAll().get(0);
    assertThat(savedEntity.getTitle()).isEqualTo("The Hobbit");
    assertThat(savedEntity.getCount()).isEqualTo(10);
  }

  @Test
  @DisplayName("POST /api/v1/books - returns 400 when validation fails")
  void createBook_withInvalidTitle_returns400() throws Exception {
    BookDto.WriteRequest request = new BookDto.WriteRequest("", 5);

    mockMvc
        .perform(
            post("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));

    assertThat(bookRepository.findAll()).isEmpty();
  }

  @Test
  @DisplayName("PUT /api/v1/books/{id} - replaces book and returns 200")
  void replaceBook_withValidRequest_returns200() throws Exception {
    BookEntity book = new BookEntity();
    book.setTitle("Old Title");
    book.setCount(5);
    BookEntity saved = bookRepository.save(book);

    BookDto.WriteRequest request = new BookDto.WriteRequest("New Title", 20);

    mockMvc
        .perform(
            put("/api/v1/books/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("New Title"))
        .andExpect(jsonPath("$.count").value(20));

    BookEntity updated = bookRepository.findById(saved.getId()).orElseThrow();
    assertThat(updated.getTitle()).isEqualTo("New Title");
    assertThat(updated.getCount()).isEqualTo(20);
  }

  @Test
  @DisplayName("PUT /api/v1/books/{id} - returns 404 if book missing")
  void replaceBook_whenMissing_returns404() throws Exception {
    BookDto.WriteRequest request = new BookDto.WriteRequest("New Title", 20);

    mockMvc
        .perform(
            put("/api/v1/books/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("PATCH /api/v1/books/{id} - updates only provided fields")
  void patchBook_withValidRequest_returns200() throws Exception {
    BookEntity book = new BookEntity();
    book.setTitle("Original Title");
    book.setCount(5);
    BookEntity saved = bookRepository.save(book);

    BookDto.PatchRequest request = new BookDto.PatchRequest(null, 15);

    mockMvc
        .perform(
            patch("/api/v1/books/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Original Title"))
        .andExpect(jsonPath("$.count").value(15));

    BookEntity updated = bookRepository.findById(saved.getId()).orElseThrow();
    assertThat(updated.getTitle()).isEqualTo("Original Title");
    assertThat(updated.getCount()).isEqualTo(15);
  }

  @Test
  @DisplayName("DELETE /api/v1/books/{id} - deletes book and returns 204")
  void deleteBook_whenExists_returns204() throws Exception {
    BookEntity book = new BookEntity();
    book.setTitle("To be deleted");
    book.setCount(5);
    BookEntity saved = bookRepository.save(book);

    mockMvc.perform(delete("/api/v1/books/" + saved.getId())).andExpect(status().isNoContent());

    assertThat(bookRepository.findById(saved.getId())).isEmpty();
  }

  @Test
  @DisplayName("DELETE /api/v1/books/{id} - returns 404 when book missing")
  void deleteBook_whenMissing_returns404() throws Exception {
    mockMvc.perform(delete("/api/v1/books/999")).andExpect(status().isNotFound());
  }
}
