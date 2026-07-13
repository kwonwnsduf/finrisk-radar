package com.finrisk.radar.risk.kafka;

import com.finrisk.radar.risk.event.RiskScoreRequestedEvent;
import com.finrisk.radar.risk.service.RiskCalculationExecutionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RiskRequestedConsumer {
  private final RiskCalculationExecutionService executions;

  public RiskRequestedConsumer(RiskCalculationExecutionService e) {
    executions = e;
  }

  @KafkaListener(
      topics = RiskTopics.REQUESTED,
      groupId = "finrisk-risk-calculator",
      containerFactory = "riskKafkaListenerContainerFactory")
  public void consume(RiskScoreRequestedEvent event) {
    executions.execute(event.jobId());
  }
}
