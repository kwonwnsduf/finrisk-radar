package com.finrisk.radar.financial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finrisk.radar.collector.storage.S3Properties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class FinancialStorageConfiguration {
	@Bean
	FinancialRawStorage financialRawStorage(
			S3Properties properties, ObjectMapper mapper, ObjectProvider<S3Client> clients) {
		S3Client client = clients.getIfAvailable();
		if (!properties.configured() || client == null) return new UnavailableFinancialRawStorage();
		return new S3FinancialRawStorage(client, mapper, properties.bucket());
	}
}
