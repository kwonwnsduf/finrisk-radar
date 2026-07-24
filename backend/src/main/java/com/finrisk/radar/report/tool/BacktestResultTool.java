package com.finrisk.radar.report.tool;

import com.finrisk.radar.backtest.BacktestStatus;
import com.finrisk.radar.backtest.api.BacktestJobResponse;
import com.finrisk.radar.backtest.service.BacktestQueryService;
import com.finrisk.radar.global.error.*;
import com.finrisk.radar.user.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class BacktestResultTool {
  private final BacktestQueryService backtests;
  private final UserRepository users;

  public BacktestResultTool(BacktestQueryService backtests, UserRepository users) {
    this.backtests = backtests;
    this.users = users;
  }

  public BacktestJobResponse load(UUID jobId, Long userId) {
    Role role =
        users
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED))
            .getRole();
    BacktestJobResponse job = backtests.getForUser(jobId, userId, role);
    if (job.result() == null || job.status() != BacktestStatus.COMPLETED)
      throw new BusinessException(ErrorCode.REPORT_DATA_INSUFFICIENT);
    return job;
  }
}
