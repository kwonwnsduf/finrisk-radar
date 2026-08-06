package com.finrisk.radar.cron;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

class Day20CronTaskRunnerTest {
  @Test
  void runsOnlySelectedTaskAndClosesContext() {
    Day20CronTask selected = mock(Day20CronTask.class);
    Day20CronTask other = mock(Day20CronTask.class);
    when(selected.name()).thenReturn("market-data");
    when(other.name()).thenReturn("risk-recalculation");
    GenericApplicationContext context = new GenericApplicationContext();
    context.refresh();

    new Day20CronTaskRunner("market-data", List.of(selected, other), context)
        .run(new DefaultApplicationArguments());

    verify(selected).run();
    verify(other, never()).run();
  }

  @Test
  void refusesToRunWhenScheduledProcessingIsActive() {
    GenericApplicationContext context = new GenericApplicationContext();
    context.registerBean(
        TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME, Object.class);
    context.refresh();
    Day20CronTask task = mock(Day20CronTask.class);

    assertThatThrownBy(
            () ->
                new Day20CronTaskRunner("market-data", List.of(task), context)
                    .run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("@Scheduled");
    verify(task, never()).run();
    context.close();
  }
}
