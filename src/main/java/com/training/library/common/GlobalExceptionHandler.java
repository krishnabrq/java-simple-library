package com.training.library.common;

import com.training.library.auth.EmailAlreadyExistsException;
import com.training.library.auth.InvalidCredentialsException;
import com.training.library.auth.InvalidTokenException;
import com.training.library.books.BookConflictException;
import com.training.library.books.BookNotFoundException;
import com.training.library.loans.LoanConflictException;
import com.training.library.loans.LoanNotFoundException;
import com.training.library.loans.LoanNotPermittedException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  // 404 — domain "book not found"
  @ExceptionHandler(BookNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleBookNotFound(BookNotFoundException ex) {
    log.debug("404 book not found: {}", ex.getMessage());
    return notFound(ex.getMessage());
  }

  // 409 — book mutation conflicts with current state (e.g. delete-with-active-loans,
  // count-below-active-loans)
  @ExceptionHandler(BookConflictException.class)
  public ResponseEntity<ErrorResponse> handleBookConflict(BookConflictException ex) {
    log.debug("409 book conflict: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse.of(409, "Conflict", ex.getMessage()));
  }

  // 409 — signup hit an existing active user with the same email
  @ExceptionHandler(EmailAlreadyExistsException.class)
  public ResponseEntity<ErrorResponse> handleEmailExists(EmailAlreadyExistsException ex) {
    log.debug("409 email exists: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse.of(409, "Conflict", ex.getMessage()));
  }

  // 401 — login or refresh authentication failed. Message intentionally generic for
  // InvalidCredentialsException so we don't leak whether an email exists.
  @ExceptionHandler({InvalidCredentialsException.class, InvalidTokenException.class})
  public ResponseEntity<ErrorResponse> handleAuthFailure(RuntimeException ex) {
    log.debug("401 auth failure: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(ErrorResponse.of(401, "Unauthorized", ex.getMessage()));
  }

  // 404 — loan id missing or belongs to a different user (we deliberately conflate the
  // two to avoid disclosing other members' loans).
  @ExceptionHandler(LoanNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleLoanNotFound(LoanNotFoundException ex) {
    log.debug("404 loan not found: {}", ex.getMessage());
    return notFound(ex.getMessage());
  }

  // 409 — borrow without available copies, or return of already-returned loan
  @ExceptionHandler(LoanConflictException.class)
  public ResponseEntity<ErrorResponse> handleLoanConflict(LoanConflictException ex) {
    log.debug("409 loan conflict: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse.of(409, "Conflict", ex.getMessage()));
  }

  // 403 — authenticated, but caller's live role can't perform this action (e.g. STAFF
  // tries to borrow). Distinct from Spring Security's AccessDeniedException, which fires
  // from @PreAuthorize and is handled by the AccessDeniedHandler in SecurityConfig.
  @ExceptionHandler(LoanNotPermittedException.class)
  public ResponseEntity<ErrorResponse> handleLoanForbidden(LoanNotPermittedException ex) {
    log.debug("403 loan not permitted: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ErrorResponse.of(403, "Forbidden", ex.getMessage()));
  }

  // 404 — route not found (catches unmatched URLs, replaces Whitelabel page)
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
    log.debug("404 no route: /{}", ex.getResourcePath());
    return notFound("No endpoint at /" + ex.getResourcePath());
  }

  // 400 — request body failed Bean Validation (@NotBlank, @Size, @Max, etc.)
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
    List<ErrorResponse.FieldError> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
            .toList();
    log.debug("400 body validation failed: {} field error(s)", fieldErrors.size());
    return badRequest("Validation failed", fieldErrors);
  }

  // 400 — method parameter (e.g. @PathVariable, @RequestParam) failed
  // Bean Validation constraints like @Min, @Pattern, etc.
  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ErrorResponse> handleParameterValidation(
      HandlerMethodValidationException ex) {
    List<ErrorResponse.FieldError> fieldErrors =
        ex.getParameterValidationResults().stream()
            .flatMap(
                result ->
                    result.getResolvableErrors().stream()
                        .map(
                            err ->
                                new ErrorResponse.FieldError(
                                    result.getMethodParameter().getParameterName(),
                                    err.getDefaultMessage())))
            .toList();
    log.debug("400 parameter validation failed: {} field error(s)", fieldErrors.size());
    return badRequest("Validation failed", fieldErrors);
  }

  // 400 — path/query parameter couldn't be converted to the declared type
  // (e.g. "hhjg" for a Long path variable)
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    String expectedType =
        ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
    String message = "Parameter '" + ex.getName() + "' must be of type " + expectedType;
    log.debug("400 type mismatch on '{}': expected {}", ex.getName(), expectedType);
    return badRequest(message, null);
  }

  // 400 — request body wasn't valid JSON (malformed, wrong content-type, etc.)
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
    log.debug("400 unreadable request body");
    return badRequest("Malformed JSON request", null);
  }

  private ResponseEntity<ErrorResponse> notFound(String message) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse.of(404, "NotFound", message));
  }

  private ResponseEntity<ErrorResponse> badRequest(
      String message, List<ErrorResponse.FieldError> errors) {
    ErrorResponse body =
        errors == null
            ? ErrorResponse.of(400, "BadRequest", message)
            : ErrorResponse.of(400, "BadRequest", message, errors);
    return ResponseEntity.badRequest().body(body);
  }
}
