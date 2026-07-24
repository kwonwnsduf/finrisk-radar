package com.finrisk.radar.rag;

import com.finrisk.radar.document.DocumentContentScope;
import com.finrisk.radar.document.DocumentSourceType;
import com.finrisk.radar.document.DocumentType;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class RagVectorSearchRepository {
  private static final String ACTIVE_GENERATION =
      """
      FROM document_chunks c
      JOIN document_embedding_jobs j ON j.job_id = c.job_id
      JOIN documents d ON d.id = c.document_id
      WHERE j.active = TRUE
        AND j.status = 'COMPLETED'
        AND j.embedding_model = :currentModel
        AND j.embedding_dimensions = 1536
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public RagVectorSearchRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<RagSearchHit> search(float[] query, String currentModel, RagSearchCriteria criteria) {
    StringBuilder sql =
        new StringBuilder(
                """
                SELECT c.id AS chunk_id, c.document_id, c.chunk_index,
                       c.sentence_start_index, c.sentence_end_index,
                       c.content AS chunk_content, d.title, d.document_type, d.source_type,
                       d.content_scope, d.source_name, d.source_url, d.published_at,
                       1 - (c.embedding <=> CAST(:embedding AS vector)) AS similarity
                """)
            .append(ACTIVE_GENERATION);
    MapSqlParameterSource parameters =
        new MapSqlParameterSource()
            .addValue("embedding", DocumentChunkRepository.vector(query))
            .addValue("currentModel", currentModel)
            .addValue("limit", criteria.limit());
    if (criteria.assetId() != null) {
      sql.append(
          " AND EXISTS (SELECT 1 FROM document_asset_mappings dam WHERE dam.document_id = d.id AND"
              + " dam.asset_id = :assetId)");
      parameters.addValue("assetId", criteria.assetId());
    }
    if (criteria.documentType() != null) {
      sql.append(" AND d.document_type = :documentType");
      parameters.addValue("documentType", criteria.documentType().name());
    }
    if (criteria.sourceType() != null) {
      sql.append(" AND d.source_type = :sourceType");
      parameters.addValue("sourceType", criteria.sourceType().name());
    }
    if (criteria.publishedFrom() != null) {
      sql.append(" AND d.published_at >= :publishedFrom");
      parameters.addValue(
          "publishedFrom", Timestamp.valueOf(criteria.publishedFrom().atStartOfDay()));
    }
    if (criteria.publishedTo() != null) {
      sql.append(" AND d.published_at < :publishedToExclusive");
      parameters.addValue(
          "publishedToExclusive",
          Timestamp.valueOf(criteria.publishedTo().plusDays(1).atStartOfDay()));
    }
    if (criteria.minimumSimilarity() != null) {
      sql.append(" AND 1 - (c.embedding <=> CAST(:embedding AS vector)) >= :minimumSimilarity");
      parameters.addValue("minimumSimilarity", criteria.minimumSimilarity());
    }
    sql.append(
        " ORDER BY c.embedding <=> CAST(:embedding AS vector), d.published_at DESC NULLS LAST, c.id"
            + " DESC LIMIT :limit");
    return jdbc.query(
        sql.toString(),
        parameters,
        (rs, row) ->
            new RagSearchHit(
                rs.getLong("chunk_id"),
                rs.getLong("document_id"),
                rs.getInt("chunk_index"),
                rs.getInt("sentence_start_index"),
                rs.getInt("sentence_end_index"),
                rs.getString("title"),
                rs.getString("chunk_content"),
                rs.getDouble("similarity"),
                DocumentType.valueOf(rs.getString("document_type")),
                DocumentSourceType.valueOf(rs.getString("source_type")),
                DocumentContentScope.valueOf(rs.getString("content_scope")),
                rs.getString("source_name"),
                rs.getString("source_url"),
                rs.getTimestamp("published_at") == null
                    ? null
                    : rs.getTimestamp("published_at").toLocalDateTime()));
  }
}
