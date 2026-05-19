package com.training.library.stats;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@Slf4j
public class LibraryStatsRepository {

  private final JdbcTemplate jdbc;

  private MeterRegistry meterRegistry;

  @Autowired(required = false)
  public void setMeterRegistry(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    log.info("MeterRegistry wired; stats queries will be timed");
  }

  public long totalActiveLoans() {
    return timed(
        "library.stats.active_loans",
        () -> {
          Long c =
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM book_loans WHERE returned_at IS NULL AND deleted_at IS NULL",
                  Long.class);
          return c == null ? 0L : c;
        });
  }

  public long totalBooks() {
    return timed(
        "library.stats.total_books",
        () -> {
          Long c =
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM books WHERE deleted_at IS NULL", Long.class);
          return c == null ? 0L : c;
        });
  }

  public long totalMembers() {
    return timed(
        "library.stats.total_members",
        () -> {
          Long c =
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM users WHERE role = 'MEMBER' AND deleted_at IS NULL",
                  Long.class);
          return c == null ? 0L : c;
        });
  }

  public List<TopBorrowedBook> topBorrowedBooks(int limit) {
    return timed(
        "library.stats.top_borrowed",
        () ->
            jdbc.query(
                """
                SELECT b.id, b.title, COUNT(l.id) AS borrow_count
                FROM books b
                LEFT JOIN book_loans l
                  ON l.book_id = b.id AND l.deleted_at IS NULL
                WHERE b.deleted_at IS NULL
                GROUP BY b.id, b.title
                ORDER BY borrow_count DESC, b.id ASC
                LIMIT ?
                """,
                (rs, rowNum) ->
                    new TopBorrowedBook(
                        rs.getLong("id"), rs.getString("title"), rs.getLong("borrow_count")),
                limit));
  }

  private <T> T timed(String metric, java.util.function.Supplier<T> work) {
    if (meterRegistry == null) {
      return work.get();
    }
    Sample sample = Timer.start(meterRegistry);
    try {
      return work.get();
    } finally {
      sample.stop(meterRegistry.timer(metric));
    }
  }

  public record TopBorrowedBook(Long id, String title, long borrowCount) {}

  public Map<String, Object> snapshot(int topN) {
    return Map.of(
        "total_books",
        totalBooks(),
        "total_members",
        totalMembers(),
        "total_active_loans",
        totalActiveLoans(),
        "top_borrowed",
        topBorrowedBooks(topN));
  }
}
