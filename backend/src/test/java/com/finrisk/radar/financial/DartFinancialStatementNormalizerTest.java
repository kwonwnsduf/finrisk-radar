package com.finrisk.radar.financial;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DartFinancialStatementNormalizerTest {
	private final DartFinancialStatementNormalizer normalizer = new DartFinancialStatementNormalizer(new ObjectMapper());

	@Test void normalizesFinancialMetricValuesAndRatios() {
		String payload = """
				{
				  "status": "000",
				  "list": [
				    {"account_nm": "매출액", "thstrm_amount": "1,000"},
				    {"account_nm": "영업이익", "thstrm_amount": "200"},
				    {"account_nm": "당기순이익", "thstrm_amount": "150"},
				    {"account_nm": "부채총계", "thstrm_amount": "600"},
				    {"account_nm": "자본총계", "thstrm_amount": "300"},
				    {"account_nm": "현금및현금성자산", "thstrm_amount": "100"},
				    {"account_nm": "영업활동현금흐름", "thstrm_amount": "(50)"},
				    {"account_nm": "이자비용", "thstrm_amount": "25"}
				  ]
				}
				""";

		FinancialMetricValues values = normalizer.normalize(payload);

		assertThat(values.revenue()).isEqualByComparingTo("1000");
		assertThat(values.operatingCashFlow()).isEqualByComparingTo("-50");
		assertThat(values.debtRatio()).isEqualByComparingTo(new BigDecimal("200.000000"));
		assertThat(values.interestCoverageRatio()).isEqualByComparingTo(new BigDecimal("8.000000"));
	}

	@Test void leavesRatioNullWhenDenominatorIsMissing() {
		String payload = """
				{"status": "000", "list": [
				  {"account_nm": "부채총계", "thstrm_amount": "600"},
				  {"account_nm": "영업이익", "thstrm_amount": "200"}
				]}
				""";

		FinancialMetricValues values = normalizer.normalize(payload);

		assertThat(values.debtRatio()).isNull();
		assertThat(values.interestCoverageRatio()).isNull();
	}
}
