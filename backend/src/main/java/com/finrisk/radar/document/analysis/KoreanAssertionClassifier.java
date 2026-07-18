package com.finrisk.radar.document.analysis;

import com.finrisk.radar.document.DocumentAssertionType;
import org.springframework.stereotype.Component;

@Component
public class KoreanAssertionClassifier {
  public DocumentAssertionType classify(String s) {
    String v = s.replaceAll("\\s+", "");
    if (v.matches(".*(아니다|아니며|않았다|않는다|없었다|해소됐다|해소되었다).*")) return DocumentAssertionType.NEGATED;
    if (v.matches(".*(우려|가능성|전망|예상|검토중|위기설).*")) return DocumentAssertionType.UNCERTAIN;
    return DocumentAssertionType.AFFIRMED;
  }
}
