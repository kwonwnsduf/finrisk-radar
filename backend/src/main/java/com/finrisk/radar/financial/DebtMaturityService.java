package com.finrisk.radar.financial;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.collector.storage.RawStorageException;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Service
public class DebtMaturityService {
	private static final String SAMPLE_PATH = "debt-maturity/debt_maturity_sample.csv";

	private final DebtMaturityRepository repository;
	private final AssetRepository assets;
	private final FinancialRawStorage storage;

	public DebtMaturityService(DebtMaturityRepository repository, AssetRepository assets, FinancialRawStorage storage) {
		this.repository = repository; this.assets = assets; this.storage = storage;
	}

	@Transactional(readOnly = true)
	public List<DebtMaturityResponse> getDebtMaturities(Long assetId) {
		if (!assets.existsById(assetId)) throw new BusinessException(ErrorCode.ASSET_NOT_FOUND);
		return repository.findByAssetIdOrderByMaturityDateAsc(assetId).stream()
				.map(DebtMaturityResponse::from)
				.toList();
	}

	@Transactional
	public DebtMaturityImportResponse importSample() {
		try {
			String csv = new ClassPathResource(SAMPLE_PATH).getContentAsString(StandardCharsets.UTF_8);
			storage.storeDebtMaturitySample(csv);
			int imported = 0;
			String[] lines = csv.replace("\r", "").split("\n");
			for (int i = 1; i < lines.length; i++) {
				if (lines[i].isBlank()) continue;
				String[] values = lines[i].split(",", -1);
				if (values.length != 9) throw new BusinessException(ErrorCode.INVALID_INPUT);
				Asset asset = assets.findFirstByTickerOrderByIdAsc(values[0].trim())
						.orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));
				LocalDate maturityDate = LocalDate.parse(values[2].trim());
				BigDecimal amount = new BigDecimal(values[3].trim());
				DebtType debtType = DebtType.valueOf(values[4].trim());
				if (repository.existsByAssetIdAndMaturityDateAndAmountAndDebtType(
						asset.getId(), maturityDate, amount, debtType)) continue;
				repository.save(DebtMaturity.create(asset, maturityDate, amount, debtType,
						new BigDecimal(values[5].trim()), values[6].trim(), Boolean.parseBoolean(values[7].trim())));
				imported++;
			}
			return new DebtMaturityImportResponse(imported);
		} catch (BusinessException exception) {
			throw exception;
		} catch (RawStorageException exception) {
			throw new BusinessException(ErrorCode.FINANCIAL_DATA_UNAVAILABLE);
		} catch (Exception exception) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}
}
