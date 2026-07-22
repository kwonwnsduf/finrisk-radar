package com.finrisk.radar.rag;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentChunkRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public DocumentChunkRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void replaceForJob(
      UUID jobId,
      Long documentId,
      int contentVersion,
      String model,
      int dimensions,
      List<DocumentChunk> chunks,
      List<float[]> embeddings) {
    if (chunks.size() != embeddings.size()) {
      throw new IllegalArgumentException("Chunk and embedding counts must match.");
    }
    jdbc.update("DELETE FROM document_chunks WHERE job_id = :jobId", Map.of("jobId", jobId));
    String sql =
        """
        INSERT INTO document_chunks(
          job_id, document_id, chunk_index, sentence_start_index, sentence_end_index,
          content, content_hash, embedding, embedding_model, embedding_dimensions,
          content_version, created_at, updated_at)
        VALUES(
          :jobId, :documentId, :chunkIndex, :sentenceStart, :sentenceEnd,
          :content, :contentHash, CAST(:embedding AS vector), :model, :dimensions,
          :contentVersion, :now, :now)
        """;
    LocalDateTime now = LocalDateTime.now();
    SqlParameterSource[] batch = new SqlParameterSource[chunks.size()];
    for (int i = 0; i < chunks.size(); i++) {
      DocumentChunk chunk = chunks.get(i);
      batch[i] =
          new MapSqlParameterSource()
              .addValue("jobId", jobId)
              .addValue("documentId", documentId)
              .addValue("chunkIndex", chunk.chunkIndex())
              .addValue("sentenceStart", chunk.sentenceStartIndex())
              .addValue("sentenceEnd", chunk.sentenceEndIndex())
              .addValue("content", chunk.content())
              .addValue("contentHash", chunk.contentHash())
              .addValue("embedding", vector(embeddings.get(i)))
              .addValue("model", model)
              .addValue("dimensions", dimensions)
              .addValue("contentVersion", contentVersion)
              .addValue("now", Timestamp.valueOf(now));
    }
    jdbc.batchUpdate(sql, batch);
  }

  static String vector(float[] value) {
    StringBuilder out = new StringBuilder(value.length * 10).append('[');
    for (int i = 0; i < value.length; i++) {
      if (i > 0) out.append(',');
      out.append(Float.toString(value[i]));
    }
    return out.append(']').toString();
  }
}
