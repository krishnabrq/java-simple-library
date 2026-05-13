package com.training.library.books;

import java.util.List;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface BookMapper {

  // BookView → Response. availableCount is computed as count - activeLoanCount.
  // Done as a default method so the arithmetic stays in one place — MapStruct expressions
  // are awkward to read and break easily when fields move around.
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

  // POST: build a new entity from the request. Id and audit columns are DB-assigned.
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  BookEntity toEntity(BookDto.WriteRequest request);

  // PUT: full replace of mutable fields. ISBN, id, audit columns stay as-is on the loaded entity.
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "isbn", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  void updateFromUpdateRequest(@MappingTarget BookEntity entity, BookDto.UpdateRequest request);

  // PATCH: only non-null fields overwrite. NullValuePropertyMappingStrategy.IGNORE
  // is what makes null mean "leave this field alone" instead of "set it to null".
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "isbn", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void updatePatch(@MappingTarget BookEntity entity, BookDto.PatchRequest patch);
}
