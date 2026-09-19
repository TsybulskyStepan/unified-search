package com.example.searchapp.search.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.searchapp.shared.web.RequestValidationException;
import org.junit.jupiter.api.Test;

class SearchRequestTest {
  @Test
  void trimsTheQueryAndAppliesDefaults() {
    var request = SearchRequest.of("  hello  ", null, null);

    assertThat(request.query()).isEqualTo("hello");
    assertThat(request.limit()).isEqualTo(20);
    assertThat(request.offset()).isEqualTo(0);
  }

  @Test
  void rejectsAnEmptyQueryAnOutOfRangeLimitAndANegativeOffset() {
    assertThatThrownBy(() -> SearchRequest.of(" ", 51, -1))
        .isInstanceOf(RequestValidationException.class)
        .satisfies(
            exception ->
                assertThat(((RequestValidationException) exception).errors())
                    .containsEntry("q", "must be between 1 and 200 characters")
                    .containsEntry("limit", "must be between 1 and 50")
                    .containsEntry("offset", "must be greater than or equal to 0"));
  }
}
