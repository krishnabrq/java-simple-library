-- Runs after Hibernate's schema generation on every boot. IF NOT EXISTS keeps it idempotent.
-- Partial unique indexes: enforce uniqueness only among ACTIVE (non-soft-deleted) rows.
-- JPA's @UniqueConstraint can't express a WHERE clause, so we declare them in raw SQL.

CREATE UNIQUE INDEX IF NOT EXISTS books_isbn_active_uidx
    ON books (isbn)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS staffs_email_active_uidx
    ON staffs (email)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS members_email_active_uidx
    ON members (email)
    WHERE deleted_at IS NULL;
