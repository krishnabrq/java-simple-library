package com.training.library.books;

import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookSearchCriteria {

  private String titleContains;
  private int page = 1;
  private int limit = 10;

  public boolean hasTitleFilter() {
    return titleContains != null && !titleContains.isBlank();
  }

  public String normalizedTitleFilter() {
    return hasTitleFilter() ? "%" + titleContains.trim().toLowerCase(Locale.ROOT) + "%" : null;
  }
}
