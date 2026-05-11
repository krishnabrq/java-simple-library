package com.training.library.common;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.training.library.books.BookNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BookNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleBookNotFound(BookNotFoundException ex) {
    return notFound(ex.getMessage());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
    return notFound("No endpoint at /" + ex.getResourcePath());
  }

  private ResponseEntity<Map<String, Object>> notFound(String message) {
    Map<String, Object> body = Map.of(
        "status", 404,
        "error", "NotFound",
        "message", message,
        "timestamp", Instant.now().toString());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
  }
}
