package com.training.library.reviews;

public class ReviewNotFoundException extends RuntimeException {

  public ReviewNotFoundException(Long id) {
    super("Review " + id + " not found");
  }
}
