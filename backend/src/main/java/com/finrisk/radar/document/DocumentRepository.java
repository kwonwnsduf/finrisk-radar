package com.finrisk.radar.document;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {
  Optional<Document> findBySourceTypeAndExternalId(DocumentSourceType source, String externalId);

  List<Document> findAllByOrderByPublishedAtDescIdDesc();
}
