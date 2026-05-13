package com.training.library.books;

// Projection used by BookRepository.findAllWithAvailability / findByIdWithAvailability.
// Carries the entity alongside the live aggregate of active (un-returned, not soft-deleted)
// loans for that book, so listing/viewing produces availability in a single SQL round trip
// instead of issuing one COUNT per book (N+1).
public record BookView(BookEntity book, Long activeLoanCount) {}
