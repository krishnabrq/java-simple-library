package com.training.library.common;

import com.training.library.books.BookNotFoundException;
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
