package com.training.library.books;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("/api/v1/books")
public class BookController {

  @GetMapping
  public Book[] list(){
    Book[] books = {new Book(1l, "Title 01", 4)};
    return books;
  }

}
