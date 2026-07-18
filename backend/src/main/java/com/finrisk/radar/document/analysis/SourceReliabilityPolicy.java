package com.finrisk.radar.document.analysis;

import com.finrisk.radar.document.*;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SourceReliabilityPolicy {
  private static final Set<String> MAJOR =
      Set.of("yna.co.kr", "reuters.com", "bloomberg.com", "hankyung.com", "mk.co.kr");

  public BigDecimal reliability(Document d) {
    if (d.getSourceType() == DocumentSourceType.OPEN_DART) return BigDecimal.ONE;
    String n = d.getSourceName() == null ? "" : d.getSourceName().toLowerCase();
    return MAJOR.stream().anyMatch(n::endsWith) ? new BigDecimal("0.75") : new BigDecimal("0.60");
  }
}
