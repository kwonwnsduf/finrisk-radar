package com.finrisk.radar.document.storage;

import com.finrisk.radar.document.DocumentSourceType;
import java.time.LocalDate;
import java.util.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class S3DocumentRawStorage implements DocumentRawStorage {
  private final S3Client client;
  private final String bucket;

  public S3DocumentRawStorage(S3Client client, String bucket) {
    this.client = client;
    this.bucket = bucket;
  }

  public String store(
      DocumentSourceType source,
      LocalDate date,
      UUID jobId,
      String externalId,
      String extension,
      String contentType,
      byte[] payload) {
    String safe = externalId.replaceAll("[^a-zA-Z0-9._-]", "_");
    String prefix = source.name().toLowerCase(Locale.ROOT).replace('_', '-');
    String key =
        "documents/raw/%s/%04d/%02d/%02d/%s/%s.%s"
            .formatted(
                prefix,
                date.getYear(),
                date.getMonthValue(),
                date.getDayOfMonth(),
                jobId,
                safe,
                extension);
    client.putObject(
        PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
        RequestBody.fromBytes(payload));
    return "s3://" + bucket + "/" + key;
  }
}
