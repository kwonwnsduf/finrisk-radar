package com.finrisk.radar.cron;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.cron", name = "task")
public class Day20CronTaskRunner implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(Day20CronTaskRunner.class);
  private final String selectedTask;
  private final List<Day20CronTask> tasks;
  private final ConfigurableApplicationContext context;

  public Day20CronTaskRunner(
      @Value("${app.cron.task}") String selectedTask,
      List<Day20CronTask> tasks,
      ConfigurableApplicationContext context) {
    this.selectedTask = selectedTask;
    this.tasks = tasks;
    this.context = context;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (context.containsBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME)) {
      throw new IllegalStateException("Cron task refused to run while @Scheduled processing is active.");
    }
    Day20CronTask task =
        tasks.stream()
            .filter(candidate -> candidate.name().equals(selectedTask))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported cron task: " + selectedTask));
    log.info("event=cron_task_start task={} schedulingEnabled=false", selectedTask);
    task.run();
    log.info("event=cron_task_complete task={} status=success", selectedTask);
    SpringApplication.exit(context);
  }
}
