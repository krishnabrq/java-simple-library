package com.training.library.loans;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LoanMapper {

  @Mapping(target = "bookId", source = "book.id")
  @Mapping(target = "userId", source = "user.id")
  LoanDto.Response toResponse(BookLoanEntity loan);

  @Mapping(target = "book.id", source = "book.id")
  @Mapping(target = "book.name", source = "book.title")
  LoanDto.ListResponse toListResponse(BookLoanEntity loan);

  List<LoanDto.ListResponse> toListResponses(java.util.List<BookLoanEntity> loans);
}
