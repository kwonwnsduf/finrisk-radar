package com.finrisk.radar.asset;

import com.finrisk.radar.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assets")
public class AssetController {
	private final AssetService assetService;

	public AssetController(AssetService assetService) {
		this.assetService = assetService;
	}

	@PostMapping
	@SecurityRequirement(name = "bearerAuth")
	public ResponseEntity<ApiResponse<AssetResponse>> create(@Valid @RequestBody AssetCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(assetService.create(request)));
	}

	@GetMapping
	public ApiResponse<List<AssetResponse>> getAll() {
		return ApiResponse.success(assetService.getAll());
	}

	@GetMapping("/search")
	public ApiResponse<List<AssetResponse>> search(@RequestParam String keyword,
			@RequestParam(required = false) AssetType assetType) {
		return ApiResponse.success(assetService.search(keyword, assetType));
	}

	@GetMapping("/{assetId}")
	public ApiResponse<AssetResponse> get(@PathVariable Long assetId) {
		return ApiResponse.success(assetService.get(assetId));
	}
}
