package com.finrisk.radar.risk.engine;

import com.finrisk.radar.asset.AssetType;

public interface RiskRule {
  default boolean supportsAssetType(AssetType assetType) {
    return assetType == AssetType.BOND_ISSUER;
  }

  int priority();

  RiskRuleType supports();

  boolean required();

  RiskRuleResult evaluate(RiskEvaluationContext context);
}
