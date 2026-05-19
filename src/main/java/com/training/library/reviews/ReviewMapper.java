package com.training.library.reviews;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

  @Mapping(target = "bookId", source = "book.id")
  @Mapping(target = "userId", source = "user.id")
  ReviewDto.Response toResponse(ReviewEntity entity);

  List<ReviewDto.Response> toResponses(List<ReviewEntity> entities);
}
