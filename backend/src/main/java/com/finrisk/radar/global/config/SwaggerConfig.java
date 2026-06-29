package com.finrisk.radar.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

	@Bean
	OpenAPI finRiskRadarOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("FinRisk Radar API")
						.description("FinRisk Radar backend API documentation")
						.version("v1"));
	}
}
