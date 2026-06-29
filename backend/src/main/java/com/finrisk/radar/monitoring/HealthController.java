package com.finrisk.radar.monitoring;

import com.finrisk.radar.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

	@GetMapping("/health")
	public ApiResponse<String> health() {
		return ApiResponse.success("UP");
	}
}
