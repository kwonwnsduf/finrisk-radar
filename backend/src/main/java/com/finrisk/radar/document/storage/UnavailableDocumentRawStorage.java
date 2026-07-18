package com.finrisk.radar.document.storage;

import com.finrisk.radar.document.DocumentSourceType;
import java.time.LocalDate;
import java.util.UUID;

public class UnavailableDocumentRawStorage implements DocumentRawStorage {
  public String store(
      DocumentSourceType source,
      LocalDate date,
      UUID jobId,
      String externalId,
      String extension,
      String contentType,
      byte[] payload) {
    return null;
  }
}
