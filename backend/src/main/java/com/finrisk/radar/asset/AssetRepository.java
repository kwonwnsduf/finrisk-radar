package com.finrisk.radar.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {
	boolean existsByTickerAndMarket(String ticker, String market);

	Optional<Asset> findByTickerAndMarket(String ticker, String market);

	Optional<Asset> findFirstByTickerOrderByIdAsc(String ticker);

	List<Asset> findAllByOrderByNameAsc();

	@Query("""
			select asset from Asset asset
			where (:assetType is null or asset.assetType = :assetType)
			  and (
			      lower(asset.name) like lower(concat('%', :keyword, '%'))
			      or lower(asset.ticker) like lower(concat('%', :keyword, '%'))
			      or lower(coalesce(asset.market, '')) like lower(concat('%', :keyword, '%'))
			      or lower(coalesce(asset.sector, '')) like lower(concat('%', :keyword, '%'))
			  )
			order by asset.name asc
			""")
	List<Asset> search(@Param("keyword") String keyword, @Param("assetType") AssetType assetType);
}
