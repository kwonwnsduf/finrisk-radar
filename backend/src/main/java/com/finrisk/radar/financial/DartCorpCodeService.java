package com.finrisk.radar.financial;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DartCorpCodeService {
  private final DartCorpCodeRepository repository;
  private final DartClient client;
  private final DartCorpCodeParser parser;
  private final FinancialRawStorage storage;

  public DartCorpCodeService(
      DartCorpCodeRepository repository,
      DartClient client,
      DartCorpCodeParser parser,
      FinancialRawStorage storage) {
    this.repository = repository;
    this.client = client;
    this.parser = parser;
    this.storage = storage;
  }

  @Transactional
  public String findCorpCodeByStockCode(String stockCode) {
    String normalized = normalize(stockCode);
    return repository
        .findFirstByStockCode(normalized)
        .map(DartCorpCode::getCorpCode)
        .orElseGet(() -> refreshAndFind(normalized));
  }

  public String findCorpCode(Asset asset) {
    if (asset.getDartCorpCode() != null && !asset.getDartCorpCode().isBlank()) {
      return asset.getDartCorpCode();
    }
    return findCorpCodeByStockCode(asset.getTicker());
  }

  private String refreshAndFind(String stockCode) {
    String xml = client.downloadCorpCodeXml();
    storage.storeCorpCodesXml(xml);
    List<DartCorpCodeEntry> entries = parser.parse(xml);
    DartCorpCodeEntry match =
        entries.stream()
            .filter(entry -> stockCode.equals(normalizeNullable(entry.stockCode())))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.FINANCIAL_CORP_CODE_NOT_FOUND));
    DartCorpCode corpCode =
        repository
            .findById(match.corpCode())
            .orElseGet(
                () ->
                    DartCorpCode.of(
                        match.corpCode(), match.corpName(), match.stockCode(), match.modifyDate()));
    corpCode.update(match.corpName(), match.stockCode(), match.modifyDate());
    return repository.save(corpCode).getCorpCode();
  }

  private String normalize(String stockCode) {
    if (stockCode == null || stockCode.isBlank())
      throw new BusinessException(ErrorCode.INVALID_INPUT);
    return stockCode.trim();
  }

  private String normalizeNullable(String stockCode) {
    return stockCode == null || stockCode.isBlank() ? null : stockCode.trim();
  }
}
