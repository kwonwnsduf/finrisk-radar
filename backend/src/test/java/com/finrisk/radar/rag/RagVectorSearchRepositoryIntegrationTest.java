package com.finrisk.radar.rag;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RagVectorSearchRepositoryIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:0.8.2-pg17")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("rag_test")
          .withUsername("test")
          .withPassword("test");

  private JdbcTemplate jdbc;
  private RagVectorSearchRepository repository;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    repository = new RagVectorSearchRepository(new NamedParameterJdbcTemplate(dataSource));
    jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public; CREATE EXTENSION vector");
    jdbc.execute(
        """
        CREATE TABLE documents(
          id BIGINT PRIMARY KEY, title TEXT NOT NULL, document_type VARCHAR(30) NOT NULL,
          source_type VARCHAR(40) NOT NULL, content_scope VARCHAR(20) NOT NULL,
          source_name TEXT, source_url TEXT, published_at TIMESTAMP)
        """);
    jdbc.execute("CREATE TABLE document_asset_mappings(document_id BIGINT, asset_id BIGINT)");
    jdbc.execute(
        """
        CREATE TABLE document_embedding_jobs(
          job_id UUID PRIMARY KEY, active BOOLEAN NOT NULL, status VARCHAR(20) NOT NULL,
          embedding_model VARCHAR(100) NOT NULL, embedding_dimensions INTEGER NOT NULL)
        """);
    jdbc.execute(
        """
        CREATE TABLE document_chunks(
          id BIGINT PRIMARY KEY, job_id UUID NOT NULL, document_id BIGINT NOT NULL,
          chunk_index INTEGER NOT NULL, sentence_start_index INTEGER NOT NULL,
          sentence_end_index INTEGER NOT NULL, content TEXT NOT NULL,
          content_hash VARCHAR(64) NOT NULL, embedding vector(1536) NOT NULL)
        """);
  }

  @Test
  void searchesOnlyActiveCompletedCurrentModelJobsAndAllowsDuplicateHashes() {
    UUID active = UUID.randomUUID();
    UUID inactive = UUID.randomUUID();
    UUID previousModel = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO documents VALUES (1,'Active"
            + " document','DISCLOSURE','OPEN_DART','FULL_TEXT','DART','https://dart',CURRENT_TIMESTAMP),(2,'Old"
            + " model','NEWS','NAVER_NEWS','SNIPPET','News','https://news',CURRENT_TIMESTAMP)");
    jdbc.update(
        "INSERT INTO document_embedding_jobs VALUES (?,?, 'COMPLETED',"
            + " 'text-embedding-3-small',1536)",
        active,
        true);
    jdbc.update(
        "INSERT INTO document_embedding_jobs VALUES (?,?, 'COMPLETED',"
            + " 'text-embedding-3-small',1536)",
        inactive,
        false);
    jdbc.update(
        "INSERT INTO document_embedding_jobs VALUES (?,?, 'COMPLETED', 'previous-model',1536)",
        previousModel,
        true);
    float[] query = unit(0);
    insertChunk(1L, active, 1L, 0, "same-hash", unit(1));
    insertChunk(2L, active, 1L, 1, "same-hash", unit(2));
    insertChunk(3L, inactive, 1L, 0, "best-but-inactive", unit(0));
    insertChunk(4L, previousModel, 2L, 0, "old-model", unit(0));

    List<RagSearchHit> hits =
        repository.search(
            query,
            "text-embedding-3-small",
            new RagSearchCriteria(null, null, null, null, null, 20, null));

    assertThat(hits).extracting(RagSearchHit::chunkId).containsExactlyInAnyOrder(1L, 2L);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM document_chunks WHERE job_id = ? AND content_hash ="
                    + " 'same-hash'",
                Integer.class,
                active))
        .isEqualTo(2);
  }

  @Test
  void appliesAssetDateTypeAndSimilarityFilters() {
    UUID job = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO documents VALUES"
            + " (1,'Filtered','DISCLOSURE','OPEN_DART','FULL_TEXT','DART','url','2026-07-20')");
    jdbc.update("INSERT INTO document_asset_mappings VALUES (1, 7)");
    jdbc.update(
        "INSERT INTO document_embedding_jobs VALUES (?,?, 'COMPLETED',"
            + " 'text-embedding-3-small',1536)",
        job,
        true);
    insertChunk(1L, job, 1L, 0, "hash", unit(0));

    List<RagSearchHit> hits =
        repository.search(
            unit(0),
            "text-embedding-3-small",
            new RagSearchCriteria(
                7L,
                com.finrisk.radar.document.DocumentType.DISCLOSURE,
                com.finrisk.radar.document.DocumentSourceType.OPEN_DART,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                5,
                0.9));

    assertThat(hits).hasSize(1);
    assertThat(hits.get(0).similarity()).isCloseTo(1.0, within(0.0001));
  }

  private void insertChunk(
      Long id, UUID jobId, Long documentId, int index, String hash, float[] vector) {
    jdbc.update(
        "INSERT INTO document_chunks VALUES (?,?,?,?,?,?,?,?,CAST(? AS vector))",
        id,
        jobId,
        documentId,
        index,
        index,
        index,
        "content-" + id,
        hash,
        DocumentChunkRepository.vector(vector));
  }

  private float[] unit(int index) {
    float[] vector = new float[1536];
    vector[index] = 1f;
    return vector;
  }
}
