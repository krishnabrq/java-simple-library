package com.training.library.books;

import java.util.List;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface BookMapper {

  BookDto.Response toResponse(BookEntity entity);

  List<BookDto.Response> toResponses(List<BookEntity> entities);

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
