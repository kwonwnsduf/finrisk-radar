package com.finrisk.radar.backtest.service;

import com.finrisk.radar.backtest.*;
import com.finrisk.radar.backtest.api.*;
import com.finrisk.radar.global.error.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class BacktestRequestValidator {
  private static final BigDecimal MAX_INITIAL_CASH = new BigDecimal("1000000000000");

  public void validate(BacktestCreateRequest request) {
    if (request.startDate().isAfter(request.endDate())
        || request.endDate().isAfter(LocalDate.now())
        || request.startDate().plusYears(10).isBefore(request.endDate()))
      throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
    if (request.initialCash().signum() <= 0
        || request.initialCash().compareTo(MAX_INITIAL_CASH) > 0)
      throw new BusinessException(ErrorCode.BACKTEST_CONDITION_INVALID);
    if (request.strategyType() != StrategyType.CUSTOM) return;
    if (request.buyConditions().isEmpty()
        || request.sellConditions().isEmpty()
        || request.buyConditions().size() > 5
        || request.sellConditions().size() > 5)
      throw new BusinessException(ErrorCode.BACKTEST_CONDITION_INVALID);
    request.buyConditions().forEach(c -> validateCondition(c, true));
    request.sellConditions().forEach(c -> validateCondition(c, false));
  }

  private void validateCondition(StrategyCondition c, boolean buy) {
    if (c == null || c.type() == null || buy != isBuy(c.type()))
      throw new BusinessException(ErrorCode.BACKTEST_CONDITION_INVALID);
    if (c.period() != null && (c.period() < 2 || c.period() > 500))
      throw new BusinessException(ErrorCode.BACKTEST_CONDITION_INVALID);
    if ((c.type() == CustomConditionType.RSI_LESS_THAN
            || c.type() == CustomConditionType.RSI_GREATER_THAN)
        && (c.value() == null
            || c.value().compareTo(BigDecimal.ZERO) < 0
            || c.value().compareTo(new BigDecimal("100")) > 0))
      throw new BusinessException(ErrorCode.BACKTEST_CONDITION_INVALID);
    switch (c.type()) {
      case RSI_LESS_THAN, RSI_GREATER_THAN, STOP_LOSS, TAKE_PROFIT, TRAILING_STOP -> {
        if (c.value() == null) throw new BusinessException(ErrorCode.BACKTEST_CONDITION_INVALID);
      }
      case PRICE_ABOVE_MA,
          MA_CROSS_UP,
          BOLLINGER_LOWER_TOUCH,
          VOLUME_SPIKE,
          BREAKOUT,
          MA_DISCOUNT,
          DONCHIAN_HIGH_BREAKOUT,
          MOMENTUM_POSITIVE,
          PRICE_BELOW_MA,
          MA_CROSS_DOWN,
          BOLLINGER_UPPER_TOUCH,
          MA_PREMIUM,
          DONCHIAN_LOW_BREAKDOWN,
          MOMENTUM_NEGATIVE -> {
        if (c.period() == null) throw new BusinessException(ErrorCode.BACKTEST_CONDITION_INVALID);
      }
      default -> {}
    }
  }

  private boolean isBuy(CustomConditionType t) {
    return switch (t) {
      case RSI_LESS_THAN,
              PRICE_ABOVE_MA,
              MA_CROSS_UP,
              BOLLINGER_LOWER_TOUCH,
              MACD_GOLDEN_CROSS,
              VOLUME_SPIKE,
              BREAKOUT,
              MA_DISCOUNT,
              DONCHIAN_HIGH_BREAKOUT,
              MOMENTUM_POSITIVE ->
          true;
      default -> false;
    };
  }
}
