package com.example.searchapp.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.search.repository.LabelDocumentMatch;
import com.example.searchapp.search.repository.RankedDocumentMatch;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentFusionTest {
  private static final UUID LABELLED = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID UNLABELLED = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");

  @Test
  void putsLabelAdmissionsBeforeHigherRankedUntaggedDocumentsAndCombinesSignals() {
    var results =
        DocumentFusion.fuse(
            List.of(
                new LabelDocumentMatch(LABELLED, CLIENT, NOW, List.of("purpose:proof_of_address"))),
            List.of(new RankedDocumentMatch(UNLABELLED, CLIENT, NOW, 0.9)),
            List.of(
                new RankedDocumentMatch(UNLABELLED, CLIENT, NOW, 0.9),
                new RankedDocumentMatch(LABELLED, CLIENT, NOW, 0.8)));

    assertThat(results)
        .extracting(result -> result.documentId())
        .containsExactly(LABELLED, UNLABELLED);
    assertThat(results.getFirst().signals()).containsExactly("label", "semantic");
    assertThat(results.getFirst().labels()).containsExactly("purpose:proof_of_address");
    assertThat(results.get(1).signals()).containsExactly("lexical", "semantic");
  }
}
