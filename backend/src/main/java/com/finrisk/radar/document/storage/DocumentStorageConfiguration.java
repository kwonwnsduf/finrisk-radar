package com.finrisk.radar.document.storage;

import com.finrisk.radar.collector.storage.S3Properties;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class DocumentStorageConfiguration {
  @Bean
  DocumentRawStorage documentRawStorage(S3Properties p) {
    if (!p.configured()) return new UnavailableDocumentRawStorage();
    S3Client client =
        S3Client.builder()
            .region(Region.of(p.region()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(p.accessKey(), p.secretKey())))
            .build();
    return new S3DocumentRawStorage(client, p.bucket());
  }
}
