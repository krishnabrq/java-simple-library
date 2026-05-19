package com.training.library.testutil;

import com.training.library.books.BookEntity;
import lombok.Builder;

@Builder
public class BookTestData {

  @Builder.Default private String isbn = "9780261103252";
  @Builder.Default private String title = "Default Test Title";
  @Builder.Default private int count = 5;

  public BookEntity toEntity() {
    return BookEntity.builder().isbn(isbn).title(title).count(count).build();
  }
}
