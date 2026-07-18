package com.finrisk.radar.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.finrisk.radar.document.analysis.KoreanAssertionClassifier;
import org.junit.jupiter.api.Test;

class KoreanAssertionClassifierTest {
  private final KoreanAssertionClassifier classifier = new KoreanAssertionClassifier();

  @Test
  void distinguishesAffirmedUncertainAndNegated() {
    assertThat(classifier.classify("차환 실패가 발생했다.")).isEqualTo(DocumentAssertionType.AFFIRMED);
    assertThat(classifier.classify("차환 실패 우려가 있다.")).isEqualTo(DocumentAssertionType.UNCERTAIN);
    assertThat(classifier.classify("차환 실패는 아니다.")).isEqualTo(DocumentAssertionType.NEGATED);
  }
}
