package com.finrisk.radar.marketprice;

import com.finrisk.radar.global.response.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/market-prices")
public class MarketPriceController {
	private final MarketPriceService marketPriceService;

	public MarketPriceController(MarketPriceService marketPriceService) {
		this.marketPriceService = marketPriceService;
	}

	@GetMapping("/{assetId}")
	public ApiResponse<List<MarketPriceResponse>> getPrices(
			@PathVariable Long assetId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
		return ApiResponse.success(marketPriceService.getPrices(assetId, startDate, endDate));
	}
}
