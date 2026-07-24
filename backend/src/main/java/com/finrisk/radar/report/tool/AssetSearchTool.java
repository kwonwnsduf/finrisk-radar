package com.finrisk.radar.report.tool;

import com.finrisk.radar.asset.*;
import com.finrisk.radar.global.error.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AssetSearchTool {
  private final AssetRepository assets;

  public AssetSearchTool(AssetRepository assets) {
    this.assets = assets;
  }

  public Asset resolve(Long assetId, String question) {
    if (assetId != null)
      return assets
          .findById(assetId)
          .orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));
    String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT).replace(" ", "");
    List<Asset> matches =
        assets.findAll().stream()
            .filter(
                asset ->
                    normalized.contains(asset.getName().toLowerCase(Locale.ROOT).replace(" ", ""))
                        || normalized.contains(asset.getTicker().toLowerCase(Locale.ROOT)))
            .toList();
    if (matches.isEmpty()) throw new BusinessException(ErrorCode.ASSET_NOT_FOUND);
    if (matches.size() > 1) throw new BusinessException(ErrorCode.REPORT_ASSET_AMBIGUOUS);
    return matches.get(0);
  }
}
