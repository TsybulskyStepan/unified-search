package com.example.searchapp.search.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.IntegrationTest;
import com.example.searchapp.search.repository.DocumentSearchRepository;
import com.example.searchapp.search.repository.RankedDocumentMatch;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import(SearchRetrieverFailureApiIntegrationTest.FailingSemanticRetrieverConfiguration.class)
class SearchRetrieverFailureApiIntegrationTest extends IntegrationTest {
  @LocalServerPort private int port;

  @Test
  void returnsInternalServerErrorWhenTheConcurrentSemanticRetrieverFails() throws Exception {
    var response = get(port, "/search?q=retriever%20failure", TEST_API_KEY);

    assertThat(response.statusCode()).isEqualTo(500);
    assertThat(response.headers().firstValue("Content-Type").orElseThrow())
        .startsWith("application/problem+json");
    assertThat(response.body()).contains("\"status\":500").doesNotContain("semantic unavailable");
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FailingSemanticRetrieverConfiguration {
    @Bean
    @Primary
    DocumentSearchRepository failingSemanticRetriever() {
      return new DocumentSearchRepository(null, 0.0) {
        @Override
        public boolean hasSearchableTerms(String residual) {
          return true;
        }

        @Override
        public List<RankedDocumentMatch> findSemanticMatches(
            float[] queryVector, String embeddingModel) {
          throw new IllegalStateException("semantic unavailable");
        }
      };
    }
  }
}
