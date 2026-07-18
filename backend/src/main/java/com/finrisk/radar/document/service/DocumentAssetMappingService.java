package com.finrisk.radar.document.service;

import com.finrisk.radar.asset.*;
import com.finrisk.radar.document.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentAssetMappingService {
  private final AssetRepository assets;
  private final DocumentAssetMappingRepository mappings;
  private final AssetAliasRepository aliases;

  public DocumentAssetMappingService(
      AssetRepository assets,
      DocumentAssetMappingRepository mappings,
      AssetAliasRepository aliases) {
    this.assets = assets;
    this.mappings = mappings;
    this.aliases = aliases;
  }

  @Transactional
  public List<DocumentAssetMapping> map(Document document, Asset requested, String corpCode) {
    Map<Long, DocumentAssetMapping> found = new LinkedHashMap<>();
    String text = normalize(document.getTitle() + " " + document.getContent());
    add(
        found,
        document,
        requested,
        document.getSourceType() == DocumentSourceType.OPEN_DART
            ? AssetMatchMethod.CORP_CODE
            : AssetMatchMethod.COMPANY_NAME,
        corpCode == null ? requested.getName() : corpCode,
        document.getSourceType() == DocumentSourceType.OPEN_DART ? "1.00" : "0.95",
        true);
    for (Asset asset : assets.findAll()) {
      String name = normalize(asset.getName()), ticker = normalize(asset.getTicker());
      if (!ticker.isBlank() && ticker.length() >= 4 && text.contains(ticker))
        add(
            found,
            document,
            asset,
            AssetMatchMethod.TICKER,
            asset.getTicker(),
            "1.00",
            asset.getId().equals(requested.getId()));
      else if (name.length() >= 3 && text.contains(name))
        add(
            found,
            document,
            asset,
            AssetMatchMethod.COMPANY_NAME,
            asset.getName(),
            "0.95",
            asset.getId().equals(requested.getId()));
    }
    for (AssetAlias alias : aliases.findAll()) {
      if (text.contains(alias.getNormalizedAlias()))
        assets
            .findById(alias.getAssetId())
            .ifPresent(
                a ->
                    add(
                        found,
                        document,
                        a,
                        AssetMatchMethod.ALIAS,
                        alias.getAlias(),
                        "0.90",
                        a.getId().equals(requested.getId())));
    }
    return mappings.saveAll(found.values());
  }

  private void add(
      Map<Long, DocumentAssetMapping> found,
      Document d,
      Asset a,
      AssetMatchMethod method,
      String value,
      String confidence,
      boolean primary) {
    if (mappings.findByDocumentIdAndAssetId(d.getId(), a.getId()).isPresent()) return;
    found.putIfAbsent(
        a.getId(),
        DocumentAssetMapping.create(
            d.getId(),
            a.getId(),
            method,
            value == null ? a.getName() : value,
            new BigDecimal(confidence),
            primary));
  }

  public static String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-z가-힣]", "");
  }
}
