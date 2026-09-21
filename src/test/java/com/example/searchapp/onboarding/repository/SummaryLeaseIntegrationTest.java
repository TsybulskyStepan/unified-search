package com.example.searchapp.onboarding.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.searchapp.IntegrationTest;
import com.example.searchapp.onboarding.dto.CreateClientRequest;
import com.example.searchapp.onboarding.service.Chunk;
import com.example.searchapp.onboarding.service.Classification;
import com.example.searchapp.onboarding.service.EmbeddedChunk;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link DocumentRepository#claimPending} directly (§7.2). Documents are inserted through {@link
 * DocumentRepository#insert} with a dummy chunk (mirrors {@code
 * DocumentRepositoryAtomicityIntegrationTest}), so this stays a repository-level test with no real
 * embedding model or HTTP layer involved.
 */
class SummaryLeaseIntegrationTest extends IntegrationTest {
  @Autowired private ClientRepository clients;
  @Autowired private DocumentRepository documents;
  @Autowired private JdbcClient jdbcClient;

  @Test
  void twoConcurrentClaimsNeverReturnTheSameRow() throws Exception {
    UUID clientId =
        clients
            .insert(new CreateClientRequest("Lease", "Test", "lease.test@example.com", null, null))
            .id();
    List<UUID> pendingIds =
        List.of(
            insertPendingDocument(clientId),
            insertPendingDocument(clientId),
            insertPendingDocument(clientId),
            insertPendingDocument(clientId),
            insertPendingDocument(clientId),
            insertPendingDocument(clientId));

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);
    AtomicReference<List<ClaimedSummaryJob>> claimedByA = new AtomicReference<>();
    AtomicReference<List<ClaimedSummaryJob>> claimedByB = new AtomicReference<>();
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      pool.execute(() -> claimedByA.set(claimConcurrently(ready, go)));
      pool.execute(() -> claimedByB.set(claimConcurrently(ready, go)));
      ready.await();
      go.countDown();
    } finally {
      pool.shutdown();
      assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    Set<UUID> idsA = ids(claimedByA.get());
    Set<UUID> idsB = ids(claimedByB.get());
    assertThat(idsA).doesNotContainAnyElementsOf(idsB);
    Set<UUID> combined = new HashSet<>(idsA);
    combined.addAll(idsB);
    assertThat(combined).containsExactlyInAnyOrderElementsOf(pendingIds);
  }

  @Test
  void aRowWhoseLeaseExpiresBecomesClaimableAgain() {
    UUID clientId =
        clients
            .insert(
                new CreateClientRequest("Expiry", "Test", "expiry.test@example.com", null, null))
            .id();
    UUID documentId = insertPendingDocument(clientId);

    List<ClaimedSummaryJob> firstClaim = documents.claimPending(5);
    assertThat(ids(firstClaim)).containsExactly(documentId);
    assertThat(firstClaim.get(0).attempts()).isEqualTo(1);

    // Still leased: an immediate second claim finds nothing.
    assertThat(documents.claimPending(5)).isEmpty();

    jdbcClient
        .sql("UPDATE document SET summary_lease_until = now() - interval '1 minute' WHERE id = :id")
        .param("id", documentId)
        .update();

    List<ClaimedSummaryJob> secondClaim = documents.claimPending(5);
    assertThat(ids(secondClaim)).containsExactly(documentId);
    assertThat(secondClaim.get(0).attempts()).isEqualTo(2);
  }

  private List<ClaimedSummaryJob> claimConcurrently(CountDownLatch ready, CountDownLatch go) {
    ready.countDown();
    try {
      go.await();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(exception);
    }
    return documents.claimPending(5);
  }

  private UUID insertPendingDocument(UUID clientId) {
    var document =
        documents.insert(
            clientId,
            "Title",
            "one two three",
            new Classification("unknown", List.of(), Classification.SOURCE_UNKNOWN, 0, ""),
            new float[384],
            List.of(new EmbeddedChunk(new Chunk(0, 0, 5), new float[384])),
            "test-model");
    jdbcClient
        .sql("UPDATE document SET summary_status = 'pending' WHERE id = :id")
        .param("id", document.id())
        .update();
    return document.id();
  }

  private static Set<UUID> ids(List<ClaimedSummaryJob> jobs) {
    return jobs.stream().map(ClaimedSummaryJob::id).collect(Collectors.toSet());
  }
}
