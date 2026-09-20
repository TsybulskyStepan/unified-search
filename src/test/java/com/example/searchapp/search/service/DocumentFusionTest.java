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

  @Test
  void sumsRankScoresSoTwoSignalsBeatOneEvenWhenEachIndividualRankIsLower() {
    UUID single = UUID.fromString("00000000-0000-0000-0000-000000000040");
    UUID both = UUID.fromString("00000000-0000-0000-0000-000000000041");
    UUID filler = UUID.fromString("00000000-0000-0000-0000-000000000042");

    var results =
        DocumentFusion.fuse(
            List.of(),
            List.of(
                new RankedDocumentMatch(single, CLIENT, NOW, 0.9),
                new RankedDocumentMatch(both, CLIENT, NOW, 0.8)),
            List.of(
                new RankedDocumentMatch(filler, CLIENT, NOW, 0.7),
                new RankedDocumentMatch(both, CLIENT, NOW, 0.6)));

    assertThat(results.getFirst().documentId()).isEqualTo(both);
    assertThat(results.getFirst().signals()).containsExactly("lexical", "semantic");
  }

  @Test
  void placesALabelOnlyDocumentAheadOfAnUntaggedDocumentWithAPositiveFusedScore() {
    var results =
        DocumentFusion.fuse(
            List.of(label(LABELLED, NOW.minusSeconds(3600))),
            List.of(new RankedDocumentMatch(UNLABELLED, CLIENT, NOW, 0.9)),
            List.of(new RankedDocumentMatch(UNLABELLED, CLIENT, NOW, 0.9)));

    assertThat(results)
        .extracting(result -> result.documentId())
        .containsExactly(LABELLED, UNLABELLED);
    assertThat(results.getFirst().signals()).containsExactly("label");
  }

  @Test
  void ordersTiesByRecencyThenIdWhateverOrderTheSignalsArrivedIn() {
    UUID newest = UUID.fromString("00000000-0000-0000-0000-000000000030");
    UUID sameTimeLowId = UUID.fromString("00000000-0000-0000-0000-000000000020");
    UUID sameTimeHighId = UUID.fromString("00000000-0000-0000-0000-000000000021");
    UUID oldest = UUID.fromString("00000000-0000-0000-0000-000000000010");
    var labelMatches =
        List.of(
            label(oldest, NOW.minusSeconds(60)),
            label(sameTimeHighId, NOW.minusSeconds(30)),
            label(newest, NOW),
            label(sameTimeLowId, NOW.minusSeconds(30)));

    var forwards = DocumentFusion.fuse(labelMatches, List.of(), List.of());
    var backwards = DocumentFusion.fuse(labelMatches.reversed(), List.of(), List.of());

    assertThat(forwards)
        .extracting(result -> result.documentId())
        .containsExactly(newest, sameTimeLowId, sameTimeHighId, oldest);
    assertThat(backwards).isEqualTo(forwards);
  }

  @Test
  void keepsEveryAdmittedDocumentExactlyOnce() {
    UUID third = UUID.fromString("00000000-0000-0000-0000-000000000003");

    var results =
        DocumentFusion.fuse(
            List.of(label(LABELLED, NOW)),
            List.of(
                new RankedDocumentMatch(UNLABELLED, CLIENT, NOW, 0.9),
                new RankedDocumentMatch(LABELLED, CLIENT, NOW, 0.8)),
            List.of(
                new RankedDocumentMatch(third, CLIENT, NOW, 0.7),
                new RankedDocumentMatch(UNLABELLED, CLIENT, NOW, 0.6)));

    assertThat(results)
        .extracting(result -> result.documentId())
        .containsExactlyInAnyOrder(LABELLED, UNLABELLED, third);
  }

  private static LabelDocumentMatch label(UUID documentId, Instant createdAt) {
    return new LabelDocumentMatch(documentId, CLIENT, createdAt, List.of("purpose:proof"));
  }
}
