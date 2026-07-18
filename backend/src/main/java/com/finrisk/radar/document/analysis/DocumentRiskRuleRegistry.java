package com.finrisk.radar.document.analysis;

import com.finrisk.radar.risk.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DocumentRiskRuleRegistry {
  private final List<Rule> rules =
      List.of(
          new Rule(
              "REHABILITATION",
              "회생절차|법정관리",
              CreditEventType.REHABILITATION_FILED,
              RiskSeverity.CRITICAL,
              new BigDecimal("1.00")),
          new Rule(
              "ACCELERATION",
              "기한이익상실",
              CreditEventType.ACCELERATION_EVENT,
              RiskSeverity.CRITICAL,
              new BigDecimal("1.00")),
          new Rule(
              "REFINANCING_FAILURE",
              "차환\\s*실패|리파이낸싱\\s*실패",
              CreditEventType.REFINANCING_FAILURE,
              RiskSeverity.HIGH,
              new BigDecimal("0.95")),
          new Rule(
              "FUNDING_FAILURE",
              "자금조달\\s*실패|CP\\s*발행\\s*실패",
              CreditEventType.FUNDING_FAILURE,
              RiskSeverity.HIGH,
              new BigDecimal("0.90")),
          new Rule(
              "BOND_ISSUANCE_FAILURE",
              "회사채\\s*발행\\s*(?:실패|철회)",
              CreditEventType.BOND_ISSUANCE_FAILURE,
              RiskSeverity.HIGH,
              new BigDecimal("0.95")),
          new Rule(
              "RIGHTS_OFFERING_FAILURE",
              "유상증자\\s*(?:실패|철회)",
              CreditEventType.RIGHTS_OFFERING_FAILURE,
              RiskSeverity.HIGH,
              new BigDecimal("0.90")),
          new Rule(
              "CREDIT_DOWNGRADE",
              "신용등급\\s*(?:하락|강등)",
              CreditEventType.CREDIT_RATING_DOWNGRADE,
              RiskSeverity.HIGH,
              new BigDecimal("0.95")),
          new Rule(
              "NEGATIVE_OUTLOOK",
              "등급전망\\s*부정적",
              CreditEventType.NEGATIVE_OUTLOOK,
              RiskSeverity.MEDIUM,
              new BigDecimal("0.90")),
          new Rule(
              "LIQUIDITY_CRISIS",
              "유동성\\s*위기",
              CreditEventType.LIQUIDITY_CRISIS,
              RiskSeverity.HIGH,
              new BigDecimal("0.85")),
          new Rule(
              "FX_HEDGE_STRESS",
              "환헤지.*(?:부담|마진콜)",
              CreditEventType.FX_HEDGE_STRESS,
              RiskSeverity.MEDIUM,
              new BigDecimal("0.80")),
          new Rule(
              "CASH_TRAP",
              "캐시트랩\\s*(?:발동|위반)",
              CreditEventType.CASH_TRAP_TRIGGERED,
              RiskSeverity.HIGH,
              new BigDecimal("0.90")),
          new Rule(
              "DIVIDEND_REDUCTION",
              "배당\\s*(?:축소|중단)",
              CreditEventType.DIVIDEND_REDUCTION,
              RiskSeverity.MEDIUM,
              new BigDecimal("0.80")),
          new Rule(
              "LTV_BREACH",
              "LTV.*(?:한도\\s*초과|약정\\s*위반)",
              CreditEventType.LTV_COVENANT_BREACH,
              RiskSeverity.HIGH,
              new BigDecimal("0.90")));

  public List<Rule> rules() {
    return rules;
  }

  public record Rule(
      String code,
      String regex,
      CreditEventType eventType,
      RiskSeverity severity,
      BigDecimal reliability) {}
}
