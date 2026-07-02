package com.finrisk.radar.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank(message = "Email is required.")
		@Email(message = "Email format is invalid.")
		@Size(max = 255, message = "Email must be at most 255 characters.")
		String email,

		@NotBlank(message = "Password is required.")
		@Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters.")
		String password
) {
	public LoginRequest {
		email = email == null ? null : email.trim();
	}
}
