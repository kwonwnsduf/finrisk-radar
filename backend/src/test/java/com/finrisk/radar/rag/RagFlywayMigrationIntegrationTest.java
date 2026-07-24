package com.finrisk.radar.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RagFlywayMigrationIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:0.8.2-pg17")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("migration_test")
          .withUsername("test")
          .withPassword("test");

  @Test
  void latestSchemaMigratesRagAndAiReports() {
    Flyway flyway =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .load();

    assertThat(flyway.migrate().success).isTrue();

    JdbcTemplate jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    assertThat(
            jdbc.queryForObject(
                "SELECT extname FROM pg_extension WHERE extname = 'vector'", String.class))
        .isEqualTo("vector");
    assertThat(
            jdbc.queryForObject(
                "SELECT format_type(atttypid, atttypmod) FROM pg_attribute "
                    + "WHERE attrelid = 'document_chunks'::regclass AND attname = 'embedding'",
                String.class))
        .isEqualTo("vector(1536)");
    assertThat(
            jdbc.queryForList(
                "SELECT conname FROM pg_constraint WHERE conrelid = 'document_chunks'::regclass AND"
                    + " contype = 'u'",
                String.class))
        .containsExactly("uq_document_chunks_job_index");
    assertThat(jdbc.queryForObject("SELECT to_regclass('public.ai_reports')", String.class))
        .isEqualTo("ai_reports");
    assertThat(
            jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                    + "WHERE table_name = 'backtest_jobs' AND column_name LIKE 'parser_%'",
                String.class))
        .containsExactlyInAnyOrder(
            "parser_model",
            "parser_prompt_version",
            "parser_input_token_count",
            "parser_output_token_count");
    assertThat(
            jdbc.queryForList(
                "SELECT conname FROM pg_constraint "
                    + "WHERE conrelid = 'ai_reports'::regclass AND contype = 'c'",
                String.class))
        .contains("ck_ai_reports_target");
  }
}
