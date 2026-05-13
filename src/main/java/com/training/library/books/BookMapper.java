package com.training.library.books;

import java.util.List;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface BookMapper {

  default BookDto.Response toResponse(BookView view) {
    BookEntity b = view.book();
    int active = view.activeLoanCount() == null ? 0 : view.activeLoanCount().intValue();
    return new BookDto.Response(
        b.getId(),
        b.getIsbn(),
        b.getTitle(),
        b.getCount(),
        b.getCount() - active,
        b.getCreatedAt(),
        b.getUpdatedAt());
  }

  default List<BookDto.Response> toResponses(List<BookView> views) {
    return views.stream().map(this::toResponse).toList();
  }

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  BookEntity toEntity(BookDto.WriteRequest request);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "isbn", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  void updateFromUpdateRequest(@MappingTarget BookEntity entity, BookDto.UpdateRequest request);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "isbn", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void updatePatch(@MappingTarget BookEntity entity, BookDto.PatchRequest patch);
}
