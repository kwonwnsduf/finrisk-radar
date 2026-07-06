package com.finrisk.radar.usage.stub;

import com.finrisk.radar.global.response.ApiResponse;
import com.finrisk.radar.usage.UsageLimit;
import com.finrisk.radar.usage.UsageType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/day3/stubs")
public class Day3UsageStubController {
	@UsageLimit(UsageType.RISK_REPORT)
	@PostMapping("/risk-reports")
	public ApiResponse<String> riskReport() { return ApiResponse.success("RISK_REPORT_ACCEPTED"); }

	@UsageLimit(UsageType.AI_AGENT)
	@PostMapping("/ai-agent/questions")
	public ApiResponse<String> aiAgentQuestion() { return ApiResponse.success("AI_AGENT_ACCEPTED"); }
}
