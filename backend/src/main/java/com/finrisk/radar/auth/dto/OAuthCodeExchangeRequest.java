package com.finrisk.radar.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OAuthCodeExchangeRequest(
		@NotBlank(message = "OAuth code is required.")
		@Size(max = 200, message = "OAuth code must be at most 200 characters.")
		String code
) {
}
