package com.finrisk.radar.asset;

import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AssetService {
	private final AssetRepository assetRepository;

	public AssetService(AssetRepository assetRepository) {
		this.assetRepository = assetRepository;
	}

	@Transactional
	public AssetResponse create(AssetCreateRequest request) {
		String ticker = request.ticker().trim().toUpperCase(Locale.ROOT);
		String market = normalizeNullable(request.market());
		if (assetRepository.existsByTickerAndMarket(ticker, market)) {
			throw new BusinessException(ErrorCode.ASSET_ALREADY_EXISTS);
		}
		Asset asset = Asset.create(request.name(), ticker, market, request.sector(),
				request.country(), request.currency(), request.assetType());
		try {
			return AssetResponse.from(assetRepository.saveAndFlush(asset));
		} catch (DataIntegrityViolationException exception) {
			throw new BusinessException(ErrorCode.ASSET_ALREADY_EXISTS);
		}
	}

	@Transactional(readOnly = true)
	public List<AssetResponse> getAll() {
		return assetRepository.findAllByOrderByNameAsc().stream().map(AssetResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public List<AssetResponse> search(String keyword, AssetType assetType) {
		if (keyword == null || keyword.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		return assetRepository.search(keyword.trim(), assetType).stream().map(AssetResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public AssetResponse get(Long assetId) {
		return AssetResponse.from(assetRepository.findById(assetId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND)));
	}

	private static String normalizeNullable(String value) {
		if (value == null || value.isBlank()) return null;
		return value.trim().toUpperCase(Locale.ROOT);
	}
}
