package com.finrisk.radar.report.kafka;

import com.finrisk.radar.report.event.ReportGenerationRequestedEvent;
import com.finrisk.radar.report.service.ReportGenerationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ReportGenerationConsumer {
  private final ReportGenerationService reports;

  public ReportGenerationConsumer(ReportGenerationService reports) {
    this.reports = reports;
  }

  @KafkaListener(
      topics = ReportTopics.GENERATION_REQUESTED,
      groupId = "finrisk-report-worker",
      containerFactory = "reportKafkaListenerContainerFactory")
  public void consume(ReportGenerationRequestedEvent event) {
    reports.generate(event.reportId());
  }
}
