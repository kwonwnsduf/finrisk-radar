package com.finrisk.radar.risk;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRelationshipRepository extends JpaRepository<AssetRelationship, Long> {
  List<AssetRelationship> findByFromAssetId(Long assetId);
}
