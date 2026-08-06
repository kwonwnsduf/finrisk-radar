package com.finrisk.radar.risk.kafka;

import com.finrisk.radar.risk.event.RiskScoreRequestedEvent;
import com.finrisk.radar.risk.service.RiskCalculationExecutionService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RiskRequestedConsumer {
  private final RiskCalculationExecutionService executions;
  private final MeterRegistry meters;

  public RiskRequestedConsumer(RiskCalculationExecutionService e, MeterRegistry meters) {
    executions = e;
    this.meters = meters;
  }

  @KafkaListener(
      topics = RiskTopics.REQUESTED,
      groupId = "finrisk-risk-calculator",
      containerFactory = "riskKafkaListenerContainerFactory")
  public void consume(RiskScoreRequestedEvent event) {
    Timer.Sample sample = Timer.start(meters);
    try {
      executions.execute(event.jobId());
      meters.counter("worker.execution", "domain", "risk", "outcome", "success").increment();
    } catch (RuntimeException exception) {
      meters.counter("worker.execution", "domain", "risk", "outcome", "failure").increment();
      throw exception;
    } finally {
      sample.stop(meters.timer("worker.execution.duration", "domain", "risk"));
    }
  }
}
