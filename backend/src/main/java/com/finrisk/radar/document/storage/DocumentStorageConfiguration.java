package com.finrisk.radar.document.storage;

import com.finrisk.radar.collector.storage.S3Properties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class DocumentStorageConfiguration {
  @Bean
  DocumentRawStorage documentRawStorage(S3Properties p, ObjectProvider<S3Client> clients) {
    S3Client client = clients.getIfAvailable();
    if (!p.configured() || client == null) return new UnavailableDocumentRawStorage();
    return new S3DocumentRawStorage(client, p.bucket());
  }
}
