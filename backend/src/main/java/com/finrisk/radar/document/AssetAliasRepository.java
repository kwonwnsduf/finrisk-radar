package com.finrisk.radar.document;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetAliasRepository extends JpaRepository<AssetAlias, Long> {
  List<AssetAlias> findByNormalizedAlias(String normalized);

  List<AssetAlias> findByAssetId(Long assetId);
}
