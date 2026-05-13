package com.training.library.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.training.library.users.UserEntity;
import com.training.library.users.UserRepository;
import com.training.library.users.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private UserRepository userRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private JwtService jwtService;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
  }

  private UserEntity seedUser(String email, String rawPassword, UserRole role) {
    UserEntity u = new UserEntity();
    u.setName("Test User");
    u.setEmail(email);
    u.setPasswordHash(passwordEncoder.encode(rawPassword));
    u.setRole(role);
    return userRepository.save(u);
  }

  @Test
  @DisplayName("POST /api/v1/auth/signup - creates MEMBER and returns access+refresh tokens")
  void signup_happyPath_returns201WithTokens() throws Exception {
    String body =
        """
        {"signup":{"name":"Alice","email":"alice@example.test","password":"hunter2hunter2"}}
        """;

    mockMvc
        .perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tokens.access_token").isString())
        .andExpect(jsonPath("$.tokens.refresh_token").isString())
        .andExpect(jsonPath("$.tokens.token_type").value("Bearer"))
        .andExpect(jsonPath("$.tokens.expires_in").isNumber());

    UserEntity saved = userRepository.findByEmail("alice@example.test").orElseThrow();
    assertThat(saved.getRole()).isEqualTo(UserRole.MEMBER);
    // Hash, not plaintext — bcrypt output starts with $2.
    assertThat(saved.getPasswordHash()).startsWith("$2");
    assertThat(passwordEncoder.matches("hunter2hunter2", saved.getPasswordHash())).isTrue();
  }

  @Test
  @DisplayName("POST /api/v1/auth/signup - 409 when email is already registered")
  void signup_duplicateEmail_returns409() throws Exception {
    seedUser("dup@example.test", "originalpass", UserRole.MEMBER);

    String body =
        """
        {"signup":{"name":"Other","email":"dup@example.test","password":"differentpw"}}
        """;

    mockMvc
        .perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.error").value("Conflict"));
  }

  @Test
  @DisplayName("POST /api/v1/auth/signup - 400 when password is too short")
  void signup_shortPassword_returns400() throws Exception {
    String body =
        """
        {"signup":{"name":"Bob","email":"bob@example.test","password":"short"}}
        """;

    mockMvc
        .perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /api/v1/auth/login - returns tokens for STAFF account")
  void login_staffHappyPath_returnsTokens() throws Exception {
    seedUser("staff@example.test", "staffpassword", UserRole.STAFF);

    String body =
        """
        {"login":{"email":"staff@example.test","password":"staffpassword"}}
        """;

    mockMvc
        .perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokens.access_token").isString())
        .andExpect(jsonPath("$.tokens.refresh_token").isString());
  }

  @Test
  @DisplayName("POST /api/v1/auth/login - 401 when password is wrong")
  void login_wrongPassword_returns401() throws Exception {
    seedUser("u@example.test", "realpassword", UserRole.MEMBER);

    String body =
        """
        {"login":{"email":"u@example.test","password":"wrongpassword"}}
        """;

    mockMvc
        .perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));
  }

  @Test
  @DisplayName("POST /api/v1/auth/login - 401 when email is unknown")
  void login_unknownEmail_returns401() throws Exception {
    String body =
        """
        {"login":{"email":"nobody@example.test","password":"whatever1"}}
        """;

    mockMvc
        .perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("POST /api/v1/auth/refresh - mints a new access token from a refresh token")
  void refresh_happyPath_returnsNewTokens() throws Exception {
    UserEntity user = seedUser("ref@example.test", "validpassword", UserRole.MEMBER);
    String refreshToken = jwtService.issueRefreshToken(user);

    String body =
        """
        {"refresh":{"refresh_token":"%s"}}
        """
            .formatted(refreshToken);

    mockMvc
        .perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokens.access_token").isString())
        .andExpect(jsonPath("$.tokens.refresh_token").isString());
  }

  @Test
  @DisplayName("POST /api/v1/auth/refresh - 401 when caller sends an access token instead")
  void refresh_withAccessToken_returns401() throws Exception {
    UserEntity user = seedUser("ref2@example.test", "validpassword", UserRole.MEMBER);
    String accessToken = jwtService.issueAccessToken(user);

    String body =
        """
        {"refresh":{"refresh_token":"%s"}}
        """
            .formatted(accessToken);

    mockMvc
        .perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Not a refresh token"));
  }

  @Test
  @DisplayName("POST /api/v1/auth/refresh - 401 when token is unparseable junk")
  void refresh_garbageToken_returns401() throws Exception {
    String body =
        """
        {"refresh":{"refresh_token":"not-a-jwt"}}
        """;

    mockMvc
        .perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("POST /api/v1/auth/logout - returns 204")
  void logout_returns204() throws Exception {
    mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("Issued access token carries role and email claims")
  void issuedAccessToken_carriesClaims() throws Exception {
    seedUser("claims@example.test", "claimspassword", UserRole.STAFF);

    String body =
        """
        {"login":{"email":"claims@example.test","password":"claimspassword"}}
        """;

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    String accessToken = json.get("tokens").get("access_token").asText();
    // JWT body is the middle segment, base64url-encoded JSON.
    String payload =
        new String(
            java.util.Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]),
            java.nio.charset.StandardCharsets.UTF_8);
    JsonNode claims = objectMapper.readTree(payload);
    assertThat(claims.get("role").asText()).isEqualTo("STAFF");
    assertThat(claims.get("email").asText()).isEqualTo("claims@example.test");
    assertThat(claims.get("type").asText()).isEqualTo("access");
  }
}
