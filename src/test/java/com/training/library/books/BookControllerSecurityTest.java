package com.training.library.books;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.training.library.auth.JwtService;
import com.training.library.users.UserEntity;
import com.training.library.users.UserRepository;
import com.training.library.users.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BookControllerSecurityTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private BookRepository bookRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JwtService jwtService;

  @BeforeEach
  void setUp() {
    bookRepository.deleteAll();
    userRepository.deleteAll();
  }

  private UserEntity seedUser(String email, UserRole role) {
    UserEntity u = new UserEntity();
    u.setName("Test");
    u.setEmail(email);
    u.setPasswordHash(passwordEncoder.encode("anything12"));
    u.setRole(role);
    return userRepository.save(u);
  }

  private BookEntity seedBook(String isbn) {
    BookEntity b = new BookEntity();
    b.setIsbn(isbn);
    b.setTitle("A Book");
    b.setCount(3);
    return bookRepository.save(b);
  }

  private String bearer(UserEntity user) {
    return "Bearer " + jwtService.issueAccessToken(user);
  }

  @Test
  @DisplayName("GET /api/v1/books - 401 with no bearer token")
  void list_anonymous_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/books"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));
  }

  @Test
  @DisplayName("GET /api/v1/books - any authenticated user can list")
  void list_member_returns200() throws Exception {
    UserEntity member = seedUser("m@e.test", UserRole.MEMBER);
    seedBook("9780000000001");

    mockMvc
        .perform(get("/api/v1/books").header("Authorization", bearer(member)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.books.length()").value(1));
  }

  @Test
  @DisplayName("POST /api/v1/books - 403 when caller is MEMBER")
  void create_member_returns403() throws Exception {
    UserEntity member = seedUser("m@e.test", UserRole.MEMBER);

    String body =
        """
        {"book":{"isbn":"9780000000123","title":"X","count":1}}
        """;

    mockMvc
        .perform(
            post("/api/v1/books")
                .header("Authorization", bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
  }

  @Test
  @DisplayName("POST /api/v1/books - 201 when caller is STAFF")
  void create_staff_returns201() throws Exception {
    UserEntity staff = seedUser("s@e.test", UserRole.STAFF);

    String body =
        """
        {"book":{"isbn":"9780000000123","title":"X","count":1}}
        """;

    mockMvc
        .perform(
            post("/api/v1/books")
                .header("Authorization", bearer(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("DELETE /api/v1/books/{id} - 403 when caller is MEMBER")
  void delete_member_returns403() throws Exception {
    UserEntity member = seedUser("m@e.test", UserRole.MEMBER);
    BookEntity book = seedBook("9780000000001");

    mockMvc
        .perform(delete("/api/v1/books/" + book.getId()).header("Authorization", bearer(member)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("DELETE /api/v1/books/{id} - 204 when caller is STAFF")
  void delete_staff_returns204() throws Exception {
    UserEntity staff = seedUser("s@e.test", UserRole.STAFF);
    BookEntity book = seedBook("9780000000001");

    mockMvc
        .perform(delete("/api/v1/books/" + book.getId()).header("Authorization", bearer(staff)))
        .andExpect(status().isNoContent());
  }
}
