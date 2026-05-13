package com.training.library.loans;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// MapStruct flattens the FK proxies into id-only fields. Accessing `loan.getBook().getId()`
// on a lazy proxy doesn't trigger a load — Hibernate already has the id — so this is safe
// to call outside the transaction.
@Mapper(componentModel = "spring")
public interface LoanMapper {

  @Mapping(target = "bookId", source = "book.id")
  @Mapping(target = "userId", source = "user.id")
  LoanDto.Response toResponse(BookLoanEntity loan);
}
