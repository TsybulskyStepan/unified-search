package com.example.searchapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.search.repository.DocumentMatch;
import com.example.searchapp.search.repository.DocumentSearchRepository;
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
        public List<DocumentMatch> findMatches(float[] queryVector, String embeddingModel) {
          throw new IllegalStateException("semantic unavailable");
        }
      };
    }
  }
}
