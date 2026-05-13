package com.training.library.books;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<BookEntity, Long> {

  // Single-query availability for listing. LEFT JOIN keeps books with zero loans; the ON
  // predicate filters to active loans (returned_at IS NULL, deleted_at IS NULL — kept
  // explicit even though @SQLRestriction on BookLoanEntity also enforces it). The class
  // projection (`SELECT new BookView(...)`) lets Spring Data hydrate a typed page. The
  // explicit countQuery prevents Spring Data from trying to derive one from the GROUP BY.
  @Query(
      value =
          """
          SELECT new com.training.library.books.BookView(b, COUNT(l))
          FROM BookEntity b
          LEFT JOIN BookLoanEntity l
            ON l.book = b AND l.returnedAt IS NULL AND l.deletedAt IS NULL
          GROUP BY b
          ORDER BY b.id
          """,
      countQuery = "SELECT COUNT(b) FROM BookEntity b")
  Page<BookView> findAllWithAvailability(Pageable pageable);

  @Query(
      """
      SELECT new com.training.library.books.BookView(b, COUNT(l))
      FROM BookEntity b
      LEFT JOIN BookLoanEntity l
        ON l.book = b AND l.returnedAt IS NULL AND l.deletedAt IS NULL
      WHERE b.id = :id
      GROUP BY b
      """)
  Optional<BookView> findByIdWithAvailability(@Param("id") Long id);
}
