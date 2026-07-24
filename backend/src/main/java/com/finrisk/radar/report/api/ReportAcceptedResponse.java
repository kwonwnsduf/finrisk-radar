package com.finrisk.radar.report.api;

import com.finrisk.radar.report.*;
import java.util.UUID;

public record ReportAcceptedResponse(
    UUID reportId, ReportStatus status, ReportStep currentStep, boolean reused) {
  public static ReportAcceptedResponse from(AiReport r, boolean reused) {
    return new ReportAcceptedResponse(r.getId(), r.getStatus(), r.getCurrentStep(), reused);
  }
}
