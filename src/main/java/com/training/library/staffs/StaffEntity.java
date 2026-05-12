package com.training.library.staffs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "staffs")
// Soft delete: DELETE is rewritten to flip deleted_at; SELECTs filter out tombstones.
@SQLDelete(sql = "UPDATE staffs SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
class StaffEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  @Size(min = 1, max = 255)
  @Column(nullable = false, length = 255)
  private String name;

  // Partial unique index (active rows only) lives in src/main/resources/import.sql.
  @NotBlank
  @Email
  @Size(max = 255)
  @Column(nullable = false, length = 255)
  private String email;

  // NOTE: column is named `password_hash` to record the intent. Until BCrypt lands
  // (see PROGRESS.md "Deferred gaps"), writes to this column will be plaintext.
  @NotBlank
  @Size(min = 1, max = 255)
  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public StaffEntity() {}

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
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

  public void setName(String name) {
    this.name = name;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }
}
