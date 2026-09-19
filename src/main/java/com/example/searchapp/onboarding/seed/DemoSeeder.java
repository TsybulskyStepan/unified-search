package com.example.searchapp.onboarding.seed;

import com.example.searchapp.onboarding.dto.CreateClientRequest;
import com.example.searchapp.onboarding.dto.CreateDocumentRequest;
import com.example.searchapp.onboarding.entity.Client;
import com.example.searchapp.onboarding.exception.DuplicateClientEmailException;
import com.example.searchapp.onboarding.repository.ClientRepository;
import com.example.searchapp.onboarding.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds the demo corpus (§11.3) after startup so a reviewer's first {@code docker compose up} finds
 * data already in the system. Loads {@code seed/corpus.json} — the same file the eval set (§12.3)
 * reads — and writes through {@link ClientRepository} and {@link DocumentService}, the ordinary
 * creation path, so seeded documents are chunked and embedded exactly like live ones. Seeding
 * through SQL would produce chunk embeddings that came from nowhere, or from a stale model.
 *
 * <p>Gated on {@code app.seed.enabled} (env {@code SEED_ENABLED}, default {@code true}, §11.2), and
 * on the {@code client} table being empty — checked once, before seeding starts. Two instances
 * starting at once can both pass that check and race to insert the same client; each client is
 * written in its own transaction, and the email uniqueness constraint (already enforced by {@link
 * ClientRepository#insert}) lets exactly one instance win per client. This seeder treats the
 * resulting {@link DuplicateClientEmailException} as "another instance already seeded this one"
 * rather than a startup failure, and moves on to the next client.
 */
@Component
@ConditionalOnProperty(
    prefix = "app.seed",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DemoSeeder implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);
  private static final String CORPUS_RESOURCE = "/seed/corpus.json";

  private final ClientRepository clients;
  private final DocumentService documents;
  private final TransactionTemplate transactionTemplate;

  public DemoSeeder(
      ClientRepository clients,
      DocumentService documents,
      PlatformTransactionManager transactionManager) {
    this.clients = clients;
    this.documents = documents;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public void run(ApplicationArguments args) {
    seed();
  }

  /** Package-visible for direct invocation from tests, without going through startup. */
  void seed() {
    if (clients.anyExist()) {
      log.info("Seed skipped: clients already present");
      return;
    }
    DemoCorpus corpus = loadCorpus();
    int seeded = 0;
    for (DemoCorpus.DemoClient client : corpus.clients()) {
      if (seedClient(client)) {
        seeded++;
      }
    }
    log.info("Seed complete: clients_seeded={} clients_total={}", seeded, corpus.clients().size());
  }

  /**
   * Inserts one client and all of its documents in a single transaction, so a failure partway
   * through a client's documents never leaves that client half-seeded. Returns {@code false}
   * without failing startup when a concurrent instance already inserted this client's email.
   */
  boolean seedClient(DemoCorpus.DemoClient client) {
    try {
      transactionTemplate.executeWithoutResult(status -> insertClientAndDocuments(client));
      return true;
    } catch (DuplicateClientEmailException exception) {
      log.info("Seed client skipped: already seeded by another instance");
      return false;
    }
  }

  private void insertClientAndDocuments(DemoCorpus.DemoClient client) {
    Client inserted =
        clients.insert(
            new CreateClientRequest(
                client.firstName(),
                client.lastName(),
                client.email(),
                client.description(),
                client.socialLinks()));
    for (DemoCorpus.DemoDocument document : client.documents()) {
      documents.create(
          inserted.id(), new CreateDocumentRequest(document.title(), document.content()));
    }
  }

  private static DemoCorpus loadCorpus() {
    try (InputStream in = DemoSeeder.class.getResourceAsStream(CORPUS_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing seed corpus on classpath: " + CORPUS_RESOURCE);
      }
      return new ObjectMapper().readValue(in, DemoCorpus.class);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read seed corpus: " + CORPUS_RESOURCE, e);
    }
  }
}
