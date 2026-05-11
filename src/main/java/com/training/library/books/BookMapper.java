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

  // POST: build a new entity from the request. Id is assigned by the DB, so ignore it here.
  @Mapping(target = "id", ignore = true)
  BookEntity toEntity(BookDto.WriteRequest request);

  // PUT: full replace. Id stays as it is on the loaded entity.
  @Mapping(target = "id", ignore = true)
  void updateFromWriteRequest(@MappingTarget BookEntity entity, BookDto.WriteRequest request);

  // PATCH: only non-null fields overwrite. NullValuePropertyMappingStrategy.IGNORE
  // is what makes null mean "leave this field alone" instead of "set it to null".
  @Mapping(target = "id", ignore = true)
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void updatePatch(@MappingTarget BookEntity entity, BookDto.PatchRequest patch);
}
