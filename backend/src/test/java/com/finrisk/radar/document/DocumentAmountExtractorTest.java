package com.finrisk.radar.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.finrisk.radar.document.analysis.DocumentAmountExtractor;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DocumentAmountExtractorTest {
  private final DocumentAmountExtractor extractor = new DocumentAmountExtractor();

  @Test
  void extractsKoreanWonUnits() {
    var amounts = extractor.extract("300억원과 5,000억원의 회사채 발행을 철회했다.");
    assertThat(amounts)
        .extracting(a -> a.amount())
        .containsExactly(new BigDecimal("30000000000.00"), new BigDecimal("500000000000.00"));
    assertThat(amounts).allMatch(a -> "KRW".equals(a.currency()));
  }

  @Test
  void extractsPrefixedAndSuffixedForeignCurrency() {
    var amounts = extractor.extract("USD 100M 및 2.5B EUR 규모의 차환 실패 우려가 있다.");
    assertThat(amounts)
        .extracting(a -> a.amount())
        .containsExactly(new BigDecimal("100000000.00"), new BigDecimal("2500000000.00"));
    assertThat(amounts).extracting(a -> a.currency()).containsExactly("USD", "EUR");
  }

  @Test
  void selectsAmountNearestToRiskKeyword() {
    String sentence = "매출 10억원과 차환 실패 금액 300억원이 공시됐다.";
    var values = extractor.extract(sentence);
    assertThat(extractor.nearest(values, sentence.indexOf("실패")).originalText()).isEqualTo("300억원");
  }
}
