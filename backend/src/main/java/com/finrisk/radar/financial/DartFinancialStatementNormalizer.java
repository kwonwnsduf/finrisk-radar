package com.finrisk.radar.financial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class DartFinancialStatementNormalizer {
	private final ObjectMapper objectMapper;

	public DartFinancialStatementNormalizer(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

	public FinancialMetricValues normalize(String payload) {
		try {
			JsonNode rows = objectMapper.readTree(payload).path("list");
			BigDecimal revenue = find(rows, List.of("매출액", "영업수익", "수익(매출액)"));
			BigDecimal operatingIncome = find(rows, List.of("영업이익", "영업손실"));
			BigDecimal netIncome = find(rows, List.of("당기순이익", "당기순손익", "분기순이익", "반기순이익"));
			BigDecimal totalDebt = find(rows, List.of("부채총계"));
			BigDecimal totalEquity = find(rows, List.of("자본총계"));
			BigDecimal cash = find(rows, List.of("현금및현금성자산", "현금및현금성자산의 증가", "현금 및 현금성자산"));
			BigDecimal operatingCashFlow = find(rows, List.of("영업활동현금흐름", "영업활동으로 인한 현금흐름", "영업활동 현금흐름"));
			BigDecimal interestExpense = find(rows, List.of("이자비용", "금융원가", "금융비용"));
			return new FinancialMetricValues(revenue, operatingIncome, netIncome, totalDebt, totalEquity, cash,
					operatingCashFlow, interestExpense, ratio(totalDebt, totalEquity, new BigDecimal("100")),
					ratio(operatingIncome, interestExpense, BigDecimal.ONE));
		} catch (DartClientException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new DartClientException("DART financial statement could not be normalized.", exception);
		}
	}

	private BigDecimal find(JsonNode rows, List<String> names) {
		if (!rows.isArray()) return null;
		for (String name : names) {
			for (JsonNode row : rows) {
				String accountName = row.path("account_nm").asText("");
				if (accountName.replace(" ", "").contains(name.replace(" ", ""))) {
					BigDecimal amount = amount(row);
					if (amount != null) return amount;
				}
			}
		}
		return null;
	}

	private BigDecimal amount(JsonNode row) {
		for (String field : List.of("thstrm_amount", "thstrm_add_amount")) {
			String value = row.path(field).asText("");
			if (!value.isBlank() && !"-".equals(value.trim())) return parseAmount(value);
		}
		return null;
	}

	private BigDecimal parseAmount(String value) {
		String normalized = value.trim().replace(",", "");
		if (normalized.startsWith("(") && normalized.endsWith(")")) {
			normalized = "-" + normalized.substring(1, normalized.length() - 1);
		}
		return new BigDecimal(normalized);
	}

	private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator, BigDecimal multiplier) {
		if (numerator == null || denominator == null || denominator.signum() == 0) return null;
		return numerator.multiply(multiplier).divide(denominator, 6, RoundingMode.HALF_UP);
	}
}
