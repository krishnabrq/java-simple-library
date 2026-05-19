package com.training.library.reviews;

public class ReviewNotPermittedException extends RuntimeException {

  public ReviewNotPermittedException(String message) {
    super(message);
  }
}
