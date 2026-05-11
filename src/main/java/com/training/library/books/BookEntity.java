package com.training.library.books;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity(name = "books")
class BookEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String title;
  private int count;

  // No-arg constructor: required by JPA (and used by Jackson when deserializing
  // JSON).
  public BookEntity() {
  }

  public BookEntity(String title, int count) {
    this.title = title;
    this.count = count;
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public int getCount() {
    return count;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public void setCount(int count) {
    this.count = count;
  }
}
