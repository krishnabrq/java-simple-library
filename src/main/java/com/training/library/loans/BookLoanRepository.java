package com.training.library.loans;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookLoanRepository extends JpaRepository<BookLoanEntity, Long> {

  // Active loans for a book = not-yet-returned, not soft-deleted.
  // @SQLRestriction on BookLoanEntity already filters deleted_at IS NULL, so we
  // only need
  // to add the returned_at IS NULL predicate via the derived query name.
  long countByBookIdAndReturnedAtIsNull(Long bookId);

  Page<BookLoanEntity> findAllByUserId(Long userId, Pageable pageable);
}
