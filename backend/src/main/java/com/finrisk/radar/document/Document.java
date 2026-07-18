package com.finrisk.radar.document;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "documents",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_documents_source_external",
            columnNames = {"source_type", "external_id"}))
public class Document extends BaseTimeEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "document_type", nullable = false)
  private DocumentType documentType;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false)
  private DocumentSourceType sourceType;

  @Column(name = "source_name")
  private String sourceName;

  @Column(nullable = false, length = 1000)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  @Column(columnDefinition = "text")
  private String summary;

  @Column(name = "source_url", length = 2000)
  private String sourceUrl;

  @Column(name = "external_id", nullable = false, length = 500)
  private String externalId;

  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  @Column(name = "fetched_at", nullable = false)
  private LocalDateTime fetchedAt;

  @Column(name = "raw_s3_path", length = 1000)
  private String rawS3Path;

  @Column(name = "content_hash", nullable = false, length = 64)
  private String contentHash;

  @Column(name = "canonical_url_hash", length = 64)
  private String canonicalUrlHash;

  @Column(nullable = false, length = 10)
  private String language;

  @Column(name = "content_version", nullable = false)
  private int contentVersion;

  protected Document() {}

  public static Document create(
      DocumentType type,
      DocumentSourceType source,
      String sourceName,
      String title,
      String content,
      String summary,
      String sourceUrl,
      String externalId,
      LocalDateTime publishedAt,
      String rawPath,
      String contentHash,
      String urlHash) {
    Document d = new Document();
    d.documentType = type;
    d.sourceType = source;
    d.sourceName = sourceName;
    d.title = title;
    d.content = content;
    d.summary = summary;
    d.sourceUrl = sourceUrl;
    d.externalId = externalId;
    d.publishedAt = publishedAt;
    d.fetchedAt = LocalDateTime.now();
    d.rawS3Path = rawPath;
    d.contentHash = contentHash;
    d.canonicalUrlHash = urlHash;
    d.language = "ko";
    d.contentVersion = 1;
    return d;
  }

  public void refresh(String title, String content, String summary, String rawPath, String hash) {
    this.title = title;
    this.summary = summary;
    this.rawS3Path = rawPath;
    this.fetchedAt = LocalDateTime.now();
    if (!this.contentHash.equals(hash)) {
      this.content = content;
      this.contentHash = hash;
      this.contentVersion++;
    }
  }

  public Long getId() {
    return id;
  }

  public DocumentType getDocumentType() {
    return documentType;
  }

  public DocumentSourceType getSourceType() {
    return sourceType;
  }

  public String getSourceName() {
    return sourceName;
  }

  public String getTitle() {
    return title;
  }

  public String getContent() {
    return content;
  }

  public String getSummary() {
    return summary;
  }

  public String getSourceUrl() {
    return sourceUrl;
  }

  public String getExternalId() {
    return externalId;
  }

  public LocalDateTime getPublishedAt() {
    return publishedAt;
  }

  public LocalDateTime getFetchedAt() {
    return fetchedAt;
  }

  public String getRawS3Path() {
    return rawS3Path;
  }

  public String getContentHash() {
    return contentHash;
  }

  public int getContentVersion() {
    return contentVersion;
  }
}
