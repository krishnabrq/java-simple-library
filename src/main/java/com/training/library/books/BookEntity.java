package com.training.library.books;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "books")
@SQLDelete(sql = "UPDATE books SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class BookEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  @Pattern(regexp = "^(\\d{9}[\\dX]|\\d{13})$", message = "must be a valid ISBN-10 or ISBN-13")
  @Column(nullable = false, length = 13)
  private String isbn;

  @NotBlank
  @Size(min = 1, max = 1000)
  @Column(nullable = false, length = 1000)
  private String title;

  @Min(0)
  @Max(100_000)
  @Column(nullable = false)
  private int count;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public BookEntity() {}

  public Long getId() {
    return id;
  }

  public String getIsbn() {
    return isbn;
  }

  public String getTitle() {
    return title;
  }

  public int getCount() {
    return count;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setIsbn(String isbn) {
    this.isbn = isbn;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public void setCount(int count) {
    this.count = count;
  }
}
