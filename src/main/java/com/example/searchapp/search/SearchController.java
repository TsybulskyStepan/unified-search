package com.example.searchapp.search;

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
class SearchController {
  private final SearchService search;

  SearchController(SearchService search) {
    this.search = search;
  }

  @GetMapping
  ResponseEntity<List<SearchResult>> search(
      @RequestParam(required = false, name = "q") String query,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) Integer offset) {
    SearchService.SearchPage page = search.search(SearchRequest.of(query, limit, offset));
    return ResponseEntity.ok()
        .header("X-Total-Count", Integer.toString(page.total()))
        .body(page.results());
  }

  @ExceptionHandler(SearchValidationException.class)
  ProblemDetail handleValidation(SearchValidationException exception, HttpServletRequest request) {
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
