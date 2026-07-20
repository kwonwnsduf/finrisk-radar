package com.finrisk.radar.risk.kafka;

import com.finrisk.radar.asset.Asset;
import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.financial.event.FinancialDataFetchFailedEvent;
import com.finrisk.radar.financial.event.FinancialDataFetchedEvent;
import com.finrisk.radar.financial.kafka.FinancialDataTopics;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.risk.RiskCalculationJob;
import com.finrisk.radar.risk.RiskCalculationStatus;
import com.finrisk.radar.risk.event.RiskScoreFailedEvent;
import com.finrisk.radar.risk.event.RiskScoreRequestedEvent;
import com.finrisk.radar.risk.service.RiskCalculationJobService;
import com.finrisk.radar.risk.service.RiskDataPreparationService;
import java.time.Instant;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RiskDataPreparationConsumer {
  private final RiskCalculationJobService jobs;
  private final AssetRepository assets;
  private final RiskDataPreparationService preparation;
  private final RiskEventPublisher publisher;

  public RiskDataPreparationConsumer(
      RiskCalculationJobService jobs,
      AssetRepository assets,
      RiskDataPreparationService preparation,
      RiskEventPublisher publisher) {
    this.jobs = jobs;
    this.assets = assets;
    this.preparation = preparation;
    this.publisher = publisher;
  }

  @KafkaListener(topics = FinancialDataTopics.FETCHED, groupId = "finrisk-risk-data-preparation")
  public void collected(FinancialDataFetchedEvent event) {
    if (event.riskCalculationJobId() == null) return;
    RiskCalculationJob job = jobs.get(event.riskCalculationJobId());
    if (terminal(job)) return;
    try {
      Asset asset =
          assets
              .findById(job.getAssetId())
              .orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));
      preparation.prepareCollectedData(event, asset);
      if (!jobs.markRequested(job.getJobId())) return;
      publisher.publishRequested(
          new RiskScoreRequestedEvent(
              job.getJobId(), job.getAssetId(), job.getUserId(), Instant.now()));
    } catch (RiskEventPublishException exception) {
      fail(
          job,
          ErrorCode.RISK_KAFKA_PUBLISH_FAILED,
          ErrorCode.RISK_KAFKA_PUBLISH_FAILED.getMessage());
    } catch (RuntimeException exception) {
      fail(job, ErrorCode.RISK_DATA_PREPARATION_FAILED, safeMessage(exception));
    }
  }

  @KafkaListener(
      topics = FinancialDataTopics.FETCH_FAILED,
      groupId = "finrisk-risk-data-preparation")
  public void collectionFailed(FinancialDataFetchFailedEvent event) {
    if (event.riskCalculationJobId() == null) return;
    RiskCalculationJob job = jobs.get(event.riskCalculationJobId());
    if (terminal(job)) return;
    fail(job, ErrorCode.RISK_DATA_COLLECTION_FAILED, event.message());
  }

  private void fail(RiskCalculationJob job, ErrorCode error, String message) {
    String safe = message == null || message.isBlank() ? error.getMessage() : message;
    jobs.fail(job.getJobId(), error.getCode(), safe);
    publisher.publishFailed(
        new RiskScoreFailedEvent(
            job.getJobId(), job.getAssetId(), error.getCode(), safe, Instant.now()));
  }

  private boolean terminal(RiskCalculationJob job) {
    return job.getStatus() == RiskCalculationStatus.COMPLETED
        || job.getStatus() == RiskCalculationStatus.FAILED;
  }

  private String safeMessage(RuntimeException exception) {
    if (exception instanceof BusinessException business) {
      return business.getErrorCode().getMessage();
    }
    return ErrorCode.RISK_DATA_PREPARATION_FAILED.getMessage();
  }
}
