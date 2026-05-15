package com.training.library.loans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.training.library.auth.JwtService;
import com.training.library.books.BookEntity;
import com.training.library.books.BookRepository;
import com.training.library.users.UserEntity;
import com.training.library.users.UserRepository;
import com.training.library.users.UserRole;
import java.time.Instant;
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
class LoanControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private BookLoanRepository loanRepository;
  @Autowired private BookRepository bookRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JwtService jwtService;

  @BeforeEach
  void setUp() {
    loanRepository.deleteAll();
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

  private BookEntity seedBook(String isbn, int count) {
    BookEntity b = new BookEntity();
    b.setIsbn(isbn);
    b.setTitle("A Book");
    b.setCount(count);
    return bookRepository.save(b);
  }

  private String bearer(UserEntity user) {
    return "Bearer " + jwtService.issueAccessToken(user);
  }

  @Test
  @DisplayName("POST /api/v1/loans - member borrows an available book → 201")
  void borrow_member_returns201() throws Exception {
    UserEntity member = seedUser("m@e.test", UserRole.MEMBER);
    BookEntity book = seedBook("9780000000001", 2);

    String body =
        """
        {"loan":{"book_id":%d}}
        """
            .formatted(book.getId());

    mockMvc
        .perform(
            post("/api/v1/loans")
                .header("Authorization", bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.loan.id").isNumber())
        .andExpect(jsonPath("$.loan.book_id").value(book.getId()))
        .andExpect(jsonPath("$.loan.user_id").value(member.getId()))
        .andExpect(jsonPath("$.loan.borrowed_at").isString())
        .andExpect(jsonPath("$.loan.returned_at").isEmpty());

    assertThat(loanRepository.findAll()).hasSize(1);
  }

  @Test
  @DisplayName("POST /api/v1/loans - 401 with no bearer token")
  void borrow_noToken_returns401() throws Exception {
    BookEntity book = seedBook("9780000000001", 2);
    String body =
        """
        {"loan":{"book_id":%d}}
        """
            .formatted(book.getId());

    mockMvc
        .perform(post("/api/v1/loans").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));
  }

  @Test
  @DisplayName("POST /api/v1/loans - 403 when caller is STAFF (service-level role check)")
  void borrow_staff_returns403() throws Exception {
    UserEntity staff = seedUser("s@e.test", UserRole.STAFF);
    BookEntity book = seedBook("9780000000001", 2);
    String body =
        """
        {"loan":{"book_id":%d}}
        """
            .formatted(book.getId());

    mockMvc
        .perform(
            post("/api/v1/loans")
                .header("Authorization", bearer(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));

    assertThat(loanRepository.findAll()).isEmpty();
  }

  @Test
  @DisplayName("POST /api/v1/loans - 409 when no copies are available")
  void borrow_noCopies_returns409() throws Exception {
    UserEntity member = seedUser("m@e.test", UserRole.MEMBER);
    BookEntity book = seedBook("9780000000001", 1);
    seedActiveLoan(book, member);

    String body =
        """
        {"loan":{"book_id":%d}}
        """
            .formatted(book.getId());

    mockMvc
        .perform(
            post("/api/v1/loans")
                .header("Authorization", bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  @DisplayName("POST /api/v1/loans - 404 when book id doesn't exist")
  void borrow_unknownBook_returns404() throws Exception {
    UserEntity member = seedUser("m@e.test", UserRole.MEMBER);
    String body =
        """
        {"loan":{"book_id":99999}}
        """;

    mockMvc
        .perform(
            post("/api/v1/loans")
                .header("Authorization", bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("PATCH /api/v1/loans/{id}/return - sets returned_at and returns the loan")
  void returnLoan_member_returns200WithReturnedAt() throws Exception {
    UserEntity member = seedUser("m@e.test", UserRole.MEMBER);
    BookEntity book = seedBook("9780000000001", 1);
    BookLoanEntity loan = seedActiveLoan(book, member);

    mockMvc
        .perform(
            patch("/api/v1/loans/" + loan.getId() + "/return")
                .header("Authorization", bearer(member)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loan.returned_at").isString());

    BookLoanEntity reloaded = loanRepository.findById(loan.getId()).orElseThrow();
    assertThat(reloaded.getReturnedAt()).isNotNull();
  }

  @Test
  @DisplayName("PATCH /api/v1/loans/{id}/return - 404 when loan belongs to someone else")
  void returnLoan_otherUsersLoan_returns404() throws Exception {
    UserEntity owner = seedUser("owner@e.test", UserRole.MEMBER);
    UserEntity attacker = seedUser("attacker@e.test", UserRole.MEMBER);
    BookEntity book = seedBook("9780000000001", 1);
    BookLoanEntity loan = seedActiveLoan(book, owner);

    mockMvc
        .perform(
            patch("/api/v1/loans/" + loan.getId() + "/return")
                .header("Authorization", bearer(attacker)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("PATCH /api/v1/loans/{id}/return - 409 when loan is already returned")
  void returnLoan_alreadyReturned_returns409() throws Exception {
    UserEntity member = seedUser("m@e.test", UserRole.MEMBER);
    BookEntity book = seedBook("9780000000001", 1);
    BookLoanEntity loan = seedActiveLoan(book, member);
    loan.setReturnedAt(Instant.now());
    loanRepository.save(loan);

    mockMvc
        .perform(
            patch("/api/v1/loans/" + loan.getId() + "/return")
                .header("Authorization", bearer(member)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  @DisplayName("PATCH /api/v1/loans/{id}/return - 403 when caller is STAFF")
  void returnLoan_staff_returns403() throws Exception {
    UserEntity member = seedUser("m@e.test", UserRole.MEMBER);
    UserEntity staff = seedUser("s@e.test", UserRole.STAFF);
    BookEntity book = seedBook("9780000000001", 1);
    BookLoanEntity loan = seedActiveLoan(book, member);

    mockMvc
        .perform(
            patch("/api/v1/loans/" + loan.getId() + "/return")
                .header("Authorization", bearer(staff)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("GET /api/v1/books/{id} - available_count drops after a member borrows")
  void availableCount_reflectsNewLoan() throws Exception {
    UserEntity member = seedUser("m@e.test", UserRole.MEMBER);
    BookEntity book = seedBook("9780000000001", 3);
    String body =
        """
        {"loan":{"book_id":%d}}
        """
            .formatted(book.getId());

    mockMvc
        .perform(
            post("/api/v1/loans")
                .header("Authorization", bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/books/" + book.getId()).header("Authorization", bearer(member)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.book.count").value(3))
        .andExpect(jsonPath("$.book.available_count").value(2));
  }

  private BookLoanEntity seedActiveLoan(BookEntity book, UserEntity user) {
    BookLoanEntity l = new BookLoanEntity();
    l.setBook(book);
    l.setUser(user);
    l.setBorrowedAt(Instant.now().minusSeconds(60));
    return loanRepository.save(l);
  }
}
