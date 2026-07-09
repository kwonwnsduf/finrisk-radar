package com.finrisk.radar.financial;

import com.finrisk.radar.auth.jwt.CustomUserPrincipal;
import com.finrisk.radar.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/financial-metrics")
@SecurityRequirement(name = "bearerAuth")
public class FinancialMetricController {
	private final FinancialDataRequestService requests;
	private final FinancialMetricService metrics;
	private final DebtMaturityService debtMaturities;

	public FinancialMetricController(FinancialDataRequestService requests, FinancialMetricService metrics,
			DebtMaturityService debtMaturities) {
		this.requests = requests; this.metrics = metrics; this.debtMaturities = debtMaturities;
	}

	@PostMapping("/fetch")
	@Operation(summary = "Request DART financial statement collection",
			description = "Creates a financial data collection job and publishes financial-data-fetch-requested. "
					+ "DART corp_code is resolved automatically from stockCode; DART_API_KEY must be provided by environment.")
	public ResponseEntity<ApiResponse<FinancialMetricFetchResponse>> fetch(
			@AuthenticationPrincipal CustomUserPrincipal principal,
			@Valid @RequestBody FinancialMetricFetchRequest request) {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(ApiResponse.success(requests.request(principal.userId(), request)));
	}

	@GetMapping("/{assetId}")
	@Operation(summary = "Get financial metrics for an asset")
	public ApiResponse<List<FinancialMetricResponse>> getMetrics(@PathVariable Long assetId) {
		return ApiResponse.success(metrics.getMetrics(assetId));
	}

	@GetMapping("/{assetId}/debt-maturities")
	@Operation(summary = "Get debt maturity records for an asset")
	public ApiResponse<List<DebtMaturityResponse>> getDebtMaturities(@PathVariable Long assetId) {
		return ApiResponse.success(debtMaturities.getDebtMaturities(assetId));
	}

	@PostMapping("/debt-maturities/import-sample")
	@Operation(summary = "Import Day 8 sample debt maturities",
			description = "Imports development/test sample debt maturity CSV rows. "
					+ "The sample is not real investment data and exists only for risk-detection logic tests.")
	public ApiResponse<DebtMaturityImportResponse> importSampleDebtMaturities() {
		return ApiResponse.success(debtMaturities.importSample());
	}
}
