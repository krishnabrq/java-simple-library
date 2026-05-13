package com.training.library.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

// Unified principal for both library members and staff. Role distinguishes them at the
// authorization layer (Spring Security) rather than the schema layer — auth/login/refresh/
// logout flows are identical for both, so one table avoids duplicating that machinery.
// Public so other features (loans/) can reference it via @ManyToOne.
@Entity
@Table(name = "users")
// Soft delete: rows persist with deleted_at set; @SQLRestriction filters every read.
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class UserEntity {

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

  // bcrypt output (60-char "$2a$..." form). Until Phase B's encoder lands, callers may
  // still write plaintext through this column — documented at the call site.
  @NotBlank
  @Size(min = 1, max = 255)
  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  // Stored as the enum's name (string) — readable in the DB, stable across reordering of
  // the enum's constants. EnumType.ORDINAL silently shifts meanings if order ever changes.
  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private UserRole role;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public UserEntity() {}

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

  public UserRole getRole() {
    return role;
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

  public void setRole(UserRole role) {
    this.role = role;
  }
}
