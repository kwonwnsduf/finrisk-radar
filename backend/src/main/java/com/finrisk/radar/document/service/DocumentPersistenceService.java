package com.finrisk.radar.document.service;

import com.finrisk.radar.document.*;
import com.finrisk.radar.document.collector.CollectedDocument;
import com.finrisk.radar.document.storage.DocumentRawStorage;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentPersistenceService {
  private final DocumentRepository documents;
  private final DocumentRawStorage storage;
  private final DocumentContentNormalizer normalizer;

  public DocumentPersistenceService(
      DocumentRepository documents,
      DocumentRawStorage storage,
      DocumentContentNormalizer normalizer) {
    this.documents = documents;
    this.storage = storage;
    this.normalizer = normalizer;
  }

  @Transactional
  public Document persist(UUID jobId, CollectedDocument raw) {
    String content = normalizer.text(raw.content());
    String title = normalizer.text(raw.title());
    String url = normalizer.url(raw.sourceUrl());
    String external = raw.externalId();
    if (raw.sourceType() == DocumentSourceType.NAVER_NEWS) external = normalizer.hash(url);
    LocalDate storageDate =
        raw.publishedAt() == null ? LocalDate.now() : raw.publishedAt().toLocalDate();
    String path =
        storage.store(
            raw.sourceType(),
            storageDate,
            jobId,
            external,
            raw.rawExtension(),
            raw.rawContentType(),
            raw.rawPayload());
    if (raw.extractedSourcePayload() != null)
      storage.store(
          raw.sourceType(),
          storageDate,
          jobId,
          external + "-main",
          raw.extractedSourceExtension(),
          raw.extractedSourceContentType(),
          raw.extractedSourcePayload());
    String hash = normalizer.hash(content);
    String finalExternal = external;
    Document document =
        documents
            .findBySourceTypeAndExternalId(raw.sourceType(), external)
            .orElseGet(
                () ->
                    Document.create(
                        raw.documentType(),
                        raw.sourceType(),
                        raw.sourceName(),
                        title,
                        content,
                        normalizer.text(raw.summary()),
                        url,
                        finalExternal,
                        raw.publishedAt(),
                        path,
                        hash,
                        normalizer.hash(url),
                        raw.contentScope()));
    if (document.getId() != null)
      document.refresh(
          title, content, normalizer.text(raw.summary()), path, hash, raw.contentScope());
    return documents.save(document);
  }
}
