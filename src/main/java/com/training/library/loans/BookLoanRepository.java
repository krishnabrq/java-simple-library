package com.training.library.loans;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookLoanRepository extends JpaRepository<BookLoanEntity, Long> {

  long countByBookIdAndReturnedAtIsNull(Long bookId);

  Page<BookLoanEntity> findAllByUserId(Long userId, Pageable pageable);
}
