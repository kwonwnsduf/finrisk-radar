package com.finrisk.radar.marketprice;

import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MarketPriceService {
	private final MarketPriceRepository marketPriceRepository;
	private final AssetRepository assetRepository;

	public MarketPriceService(MarketPriceRepository marketPriceRepository, AssetRepository assetRepository) {
		this.marketPriceRepository = marketPriceRepository;
		this.assetRepository = assetRepository;
	}

	@Transactional(readOnly = true)
	public List<MarketPriceResponse> getPrices(Long assetId, LocalDate startDate, LocalDate endDate) {
		if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
			throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
		}
		if (!assetRepository.existsById(assetId)) {
			throw new BusinessException(ErrorCode.ASSET_NOT_FOUND);
		}
		List<MarketPrice> prices;
		if (startDate != null && endDate != null) {
			prices = marketPriceRepository.findAllByAsset_IdAndDateBetweenOrderByDateAsc(assetId, startDate, endDate);
		} else if (startDate != null) {
			prices = marketPriceRepository.findAllByAsset_IdAndDateGreaterThanEqualOrderByDateAsc(assetId, startDate);
		} else if (endDate != null) {
			prices = marketPriceRepository.findAllByAsset_IdAndDateLessThanEqualOrderByDateAsc(assetId, endDate);
		} else {
			prices = marketPriceRepository.findAllByAsset_IdOrderByDateAsc(assetId);
		}

		Map<LocalDate, MarketPrice> preferred = new LinkedHashMap<>();
		for (MarketPrice price : prices) {
			preferred.merge(price.getDate(), price,
					(existing, candidate) -> candidate.getSource().priority() > existing.getSource().priority()
							? candidate : existing);
		}
		return preferred.values().stream().map(MarketPriceResponse::from).toList();
	}
}
