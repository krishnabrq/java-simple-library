package com.training.library.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.training.library.users.UserEntity;
import com.training.library.users.UserRepository;
import com.training.library.users.UserRole;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;

  @Mock private PasswordEncoder passwordEncoder;

  @Mock private JwtService jwtService;

  @Mock private JwtDecoder jwtDecoder;

  @InjectMocks private AuthService service;

  private static UserEntity userOf(String email, String hash, UserRole role) {
    UserEntity u = new UserEntity();
    u.setName("Test");
    u.setEmail(email);
    u.setPasswordHash(hash);
    u.setRole(role);
    return u;
  }

  private static Jwt jwtOf(String subject, String type) {
    return Jwt.withTokenValue("opaque")
        .header("alg", "HS256")
        .subject(subject)
        .claim(JwtService.CLAIM_TYPE, type)
        .claim(JwtService.CLAIM_ROLE, "MEMBER")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .build();
  }

  @Test
  @DisplayName("signup - hashes password, saves with MEMBER role, returns token pair")
  void signup_happyPath_savesAndIssues() {
    AuthDto.SignupRequest req = new AuthDto.SignupRequest("Alice", "a@e.test", "rawpassword");

    when(userRepository.findByEmail("a@e.test")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("rawpassword")).thenReturn("HASHED");
    when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    when(jwtService.issueAccessToken(any())).thenReturn("access-jwt");
    when(jwtService.issueRefreshToken(any())).thenReturn("refresh-jwt");

    AuthService.TokenPair pair = service.signup(req);

    assertThat(pair.access()).isEqualTo("access-jwt");
    assertThat(pair.refresh()).isEqualTo("refresh-jwt");

    ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(captor.capture());
    UserEntity saved = captor.getValue();
    assertThat(saved.getName()).isEqualTo("Alice");
    assertThat(saved.getEmail()).isEqualTo("a@e.test");
    assertThat(saved.getPasswordHash()).isEqualTo("HASHED");
    assertThat(saved.getRole()).isEqualTo(UserRole.MEMBER);
  }

  @Test
  @DisplayName("signup - throws when email is already registered to an active user")
  void signup_duplicateEmail_throws() {
    AuthDto.SignupRequest req = new AuthDto.SignupRequest("Alice", "a@e.test", "rawpassword");
    when(userRepository.findByEmail("a@e.test"))
        .thenReturn(Optional.of(userOf("a@e.test", "h", UserRole.MEMBER)));

    assertThatThrownBy(() -> service.signup(req))
        .isInstanceOf(EmailAlreadyExistsException.class)
        .hasMessageContaining("a@e.test");

    verify(passwordEncoder, never()).encode(any());
    verify(userRepository, never()).save(any());
    verify(jwtService, never()).issueAccessToken(any());
  }

  @Test
  @DisplayName("login - returns tokens when password matches")
  void login_happyPath_returnsTokens() {
    AuthDto.LoginRequest req = new AuthDto.LoginRequest("a@e.test", "rawpassword");
    UserEntity user = userOf("a@e.test", "HASH", UserRole.STAFF);

    when(userRepository.findByEmail("a@e.test")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("rawpassword", "HASH")).thenReturn(true);
    when(jwtService.issueAccessToken(user)).thenReturn("access-jwt");
    when(jwtService.issueRefreshToken(user)).thenReturn("refresh-jwt");

    AuthService.TokenPair pair = service.login(req);

    assertThat(pair.access()).isEqualTo("access-jwt");
    assertThat(pair.refresh()).isEqualTo("refresh-jwt");
  }

  @Test
  @DisplayName("login - throws InvalidCredentials when email is unknown")
  void login_unknownEmail_throwsInvalidCredentials() {
    AuthDto.LoginRequest req = new AuthDto.LoginRequest("nobody@e.test", "rawpassword");
    when(userRepository.findByEmail("nobody@e.test")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.login(req)).isInstanceOf(InvalidCredentialsException.class);

    verify(passwordEncoder, never()).matches(any(), any());
  }

  @Test
  @DisplayName("login - throws InvalidCredentials when password mismatches")
  void login_wrongPassword_throwsInvalidCredentials() {
    AuthDto.LoginRequest req = new AuthDto.LoginRequest("a@e.test", "wrongpass");
    UserEntity user = userOf("a@e.test", "HASH", UserRole.MEMBER);

    when(userRepository.findByEmail("a@e.test")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrongpass", "HASH")).thenReturn(false);

    assertThatThrownBy(() -> service.login(req)).isInstanceOf(InvalidCredentialsException.class);

    verify(jwtService, never()).issueAccessToken(any());
  }

  @Test
  @DisplayName("refresh - issues new tokens for a valid refresh JWT pointing at a live user")
  void refresh_happyPath_issuesNewTokens() {
    when(jwtDecoder.decode("good-refresh")).thenReturn(jwtOf("42", JwtService.TYPE_REFRESH));
    UserEntity user = userOf("a@e.test", "HASH", UserRole.MEMBER);
    when(userRepository.findById(42L)).thenReturn(Optional.of(user));
    when(jwtService.issueAccessToken(user)).thenReturn("new-access");
    when(jwtService.issueRefreshToken(user)).thenReturn("new-refresh");

    AuthService.TokenPair pair = service.refresh("good-refresh");

    assertThat(pair.access()).isEqualTo("new-access");
    assertThat(pair.refresh()).isEqualTo("new-refresh");
  }

  @Test
  @DisplayName("refresh - throws InvalidToken when decoder rejects the JWT")
  void refresh_decoderRejects_throwsInvalidToken() {
    when(jwtDecoder.decode("bad"))
        .thenThrow(new BadJwtException("Signed JWT rejected: Invalid signature"));

    assertThatThrownBy(() -> service.refresh("bad"))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessageContaining("invalid or expired");

    verify(userRepository, never()).findById(any());
  }

  @Test
  @DisplayName("refresh - throws InvalidToken when JWT type claim is access (not refresh)")
  void refresh_accessTokenPresented_throwsInvalidToken() {
    when(jwtDecoder.decode("access-jwt")).thenReturn(jwtOf("42", JwtService.TYPE_ACCESS));

    assertThatThrownBy(() -> service.refresh("access-jwt"))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessageContaining("Not a refresh token");

    verify(userRepository, never()).findById(any());
  }

  @Test
  @DisplayName("refresh - throws InvalidToken when subject can't be parsed as a user id")
  void refresh_malformedSubject_throwsInvalidToken() {
    when(jwtDecoder.decode("weird-sub")).thenReturn(jwtOf("not-a-number", JwtService.TYPE_REFRESH));

    assertThatThrownBy(() -> service.refresh("weird-sub"))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessageContaining("subject");

    verify(userRepository, never()).findById(any());
  }

  @Test
  @DisplayName("refresh - throws InvalidToken when the user no longer exists")
  void refresh_userMissing_throwsInvalidToken() {
    when(jwtDecoder.decode("orphan")).thenReturn(jwtOf("42", JwtService.TYPE_REFRESH));
    when(userRepository.findById(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.refresh("orphan"))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessageContaining("User no longer exists");

    verify(jwtService, never()).issueAccessToken(any());
  }

  @Test
  @DisplayName("logout - no-op (no state)")
  void logout_isNoOp() {
    service.logout();
    verify(userRepository, never()).save(any());
    verify(jwtService, never()).issueAccessToken(any());
  }
}
