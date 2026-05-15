package com.training.library.auth;

import com.training.library.notifications.UserSignedUpEvent;
import com.training.library.users.UserEntity;
import com.training.library.users.UserRepository;
import com.training.library.users.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final JwtDecoder jwtDecoder;
  private final ApplicationEventPublisher eventPublisher;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      JwtDecoder jwtDecoder,
      ApplicationEventPublisher eventPublisher) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.jwtDecoder = jwtDecoder;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public TokenPair signup(AuthDto.SignupRequest request) {
    userRepository
        .findByEmail(request.email())
        .ifPresent(
            existing -> {
              throw new EmailAlreadyExistsException(request.email());
            });

    UserEntity user = new UserEntity();
    user.setName(request.name());
    user.setEmail(request.email());
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setRole(UserRole.MEMBER);
    UserEntity saved = userRepository.save(user);
    log.info("signup ok id={} role={}", saved.getId(), saved.getRole());
    eventPublisher.publishEvent(
        new UserSignedUpEvent(saved.getId(), saved.getName(), saved.getEmail()));
    return issue(saved);
  }

  public TokenPair login(AuthDto.LoginRequest request) {
    UserEntity user =
        userRepository.findByEmail(request.email()).orElseThrow(InvalidCredentialsException::new);
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }
    log.info("login ok id={} role={}", user.getId(), user.getRole());
    return issue(user);
  }

  public TokenPair refresh(String refreshToken) {
    Jwt jwt;
    try {
      jwt = jwtDecoder.decode(refreshToken);
    } catch (JwtException e) {
      throw new InvalidTokenException("Refresh token is invalid or expired");
    }
    if (!JwtService.TYPE_REFRESH.equals(jwt.getClaimAsString(JwtService.CLAIM_TYPE))) {
      throw new InvalidTokenException("Not a refresh token");
    }

    Long userId;
    try {
      userId = Long.valueOf(jwt.getSubject());
    } catch (NumberFormatException e) {
      throw new InvalidTokenException("Refresh token subject is malformed");
    }
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new InvalidTokenException("User no longer exists"));
    log.info("refresh ok id={}", user.getId());
    return issue(user);
  }

  public void logout() {
    log.debug("logout (client-side discard, no server state)");
  }

  private TokenPair issue(UserEntity user) {
    return new TokenPair(jwtService.issueAccessToken(user), jwtService.issueRefreshToken(user));
  }

  public record TokenPair(String access, String refresh) {}
}
