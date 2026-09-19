package com.example.searchapp.search.controller;

import com.example.searchapp.search.dto.SearchRequest;
import com.example.searchapp.search.dto.SearchResult;
import com.example.searchapp.search.exception.SearchValidationException;
import com.example.searchapp.search.service.SearchService;
import com.example.searchapp.shared.web.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
public class SearchController {
  private final SearchService search;

  public SearchController(SearchService search) {
    this.search = search;
  }

  @GetMapping
  public ResponseEntity<List<SearchResult>> search(
      @RequestParam(required = false, name = "q") String query,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) Integer offset) {
    SearchService.SearchPage page = search.search(SearchRequest.of(query, limit, offset));
    return ResponseEntity.ok()
        .header("X-Total-Count", Integer.toString(page.total()))
        .body(page.results());
  }

  @ExceptionHandler(SearchValidationException.class)
  public ProblemDetail handleValidation(
      SearchValidationException exception, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetails.of(
            HttpStatus.BAD_REQUEST,
            "Validation failed",
            "One or more fields are invalid",
            URI.create(request.getRequestURI()));
    problem.setProperty("errors", exception.errors());
    return problem;
  }
}
