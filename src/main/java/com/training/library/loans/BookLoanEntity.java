package com.training.library.loans;

import com.training.library.books.BookEntity;
import com.training.library.members.MemberEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "book_loans")
// Soft delete: even loan history is preserved via deleted_at instead of physical removal.
@SQLDelete(sql = "UPDATE book_loans SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
class BookLoanEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // LAZY: the join only fires when book/member is dereferenced. Cheap list queries.
  // No cascade — lifecycle of book and member is independent of any loan row.
  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "book_id", nullable = false)
  private BookEntity book;

  // Only members borrow (staff cannot) — column named after the relationship, not the role.
  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private MemberEntity member;

  // Physical borrow moment. Distinct from created_at (which is when the row was written) so
  // back-dated loans can be recorded later. Service is responsible for setting this on create.
  @NotNull
  @Column(name = "borrowed_at", nullable = false)
  private Instant borrowedAt;

  // Null while the book is out; set when returned.
  @Column(name = "returned_at")
  private Instant returnedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public BookLoanEntity() {}

  public Long getId() {
    return id;
  }

  public BookEntity getBook() {
    return book;
  }

  public MemberEntity getMember() {
    return member;
  }

  public Instant getBorrowedAt() {
    return borrowedAt;
  }

  public Instant getReturnedAt() {
    return returnedAt;
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

  public void setBook(BookEntity book) {
    this.book = book;
  }

  public void setMember(MemberEntity member) {
    this.member = member;
  }

  public void setBorrowedAt(Instant borrowedAt) {
    this.borrowedAt = borrowedAt;
  }

  public void setReturnedAt(Instant returnedAt) {
    this.returnedAt = returnedAt;
  }
}
