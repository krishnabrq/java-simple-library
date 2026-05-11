package com.training.library.books;

public interface BookDto {

  record PatchRequest(String title, Integer count) {}
}
