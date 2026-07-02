package com.finrisk.radar.watchlist;

import com.finrisk.radar.auth.jwt.CustomUserPrincipal;
import com.finrisk.radar.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/watchlists")
public class WatchlistController {
	private final WatchlistService watchlistService;

	public WatchlistController(WatchlistService watchlistService) {
		this.watchlistService = watchlistService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<WatchlistItemResponse>> add(
			@AuthenticationPrincipal CustomUserPrincipal principal,
			@Valid @RequestBody WatchlistCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(watchlistService.add(principal.userId(), request)));
	}

	@GetMapping
	public ApiResponse<List<WatchlistItemResponse>> getAll(
			@AuthenticationPrincipal CustomUserPrincipal principal) {
		return ApiResponse.success(watchlistService.getAll(principal.userId()));
	}

	@DeleteMapping("/{itemId}")
	public ApiResponse<Void> delete(@AuthenticationPrincipal CustomUserPrincipal principal,
			@PathVariable Long itemId) {
		watchlistService.delete(principal.userId(), itemId);
		return ApiResponse.success(null);
	}
}
