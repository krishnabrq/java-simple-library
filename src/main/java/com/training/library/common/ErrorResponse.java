package com.training.library.common;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record ErrorResponse(
    int status,
    String error,
    String message,
    Instant timestamp,
    List<FieldError> errors) {

  public static ErrorResponse of(int status, String error, String message) {
    return new ErrorResponse(status, error, message, Instant.now(), null);
  }

  public static ErrorResponse of(int status, String error, String message, List<FieldError> errors) {
    return new ErrorResponse(status, error, message, Instant.now(), errors);
  }

  public record FieldError(String field, String message) {}
}
