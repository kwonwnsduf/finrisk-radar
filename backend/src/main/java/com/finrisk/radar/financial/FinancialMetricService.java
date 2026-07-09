package com.finrisk.radar.financial;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FinancialMetricService {
	private final FinancialMetricRepository repository;
	private final AssetRepository assets;

	public FinancialMetricService(FinancialMetricRepository repository, AssetRepository assets) {
		this.repository = repository; this.assets = assets;
	}

	@Transactional
	public FinancialMetric upsert(Asset asset, Integer year, Integer quarter, FinancialMetricValues values) {
		FinancialMetric metric = repository.findByAssetIdAndYearAndQuarter(asset.getId(), year, quarter)
				.orElseGet(() -> FinancialMetric.create(asset, year, quarter, values));
		metric.apply(values);
		return repository.save(metric);
	}

	@Transactional(readOnly = true)
	public List<FinancialMetricResponse> getMetrics(Long assetId) {
		if (!assets.existsById(assetId)) throw new BusinessException(ErrorCode.ASSET_NOT_FOUND);
		return repository.findByAssetIdOrderByYearDescQuarterDesc(assetId).stream()
				.map(FinancialMetricResponse::from)
				.toList();
	}
}
