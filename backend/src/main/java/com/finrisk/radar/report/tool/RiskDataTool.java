package com.finrisk.radar.report.tool;

import com.finrisk.radar.risk.api.*;
import com.finrisk.radar.risk.service.RiskQueryService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RiskDataTool {
  private final RiskQueryService risks;

  public RiskDataTool(RiskQueryService risks) {
    this.risks = risks;
  }

  public RiskData load(Long assetId) {
    RiskScoreResponse score = risks.latest(assetId);
    return new RiskData(score, risks.signalResponses(score.id()).stream().limit(20).toList());
  }

  public record RiskData(RiskScoreResponse score, List<RiskSignalResponse> signals) {}
}
