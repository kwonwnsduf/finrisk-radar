package com.finrisk.radar.cron;

import static org.assertj.core.api.Assertions.assertThat;

import com.finrisk.radar.document.DocumentSchedulingConfiguration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

class CronSchedulingIsolationContextTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(DocumentSchedulingConfiguration.class, ScheduledSentinelConfig.class)
          .withPropertyValues(
              "app.worker.enabled=false",
              "spring.task.scheduling.enabled=false",
              "app.documents.scheduler.enabled=false",
              "app.documents.recalculation-scheduler.enabled=false");

  @Test
  void cronContextDoesNotRegisterScheduledProcessorOrInvokeScheduledMethods() {
    contextRunner.run(
        context -> {
          assertThat(
                  context.containsBean(
                      TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME))
              .isFalse();
          Thread.sleep(100);
          assertThat(context.getBean(ScheduledSentinel.class).invocations()).isZero();
        });
  }

  @Configuration(proxyBeanMethods = false)
  static class ScheduledSentinelConfig {
    @Bean
    ScheduledSentinel scheduledSentinel() {
      return new ScheduledSentinel();
    }
  }

  static class ScheduledSentinel {
    private final AtomicInteger invocations = new AtomicInteger();

    @Scheduled(fixedDelay = 10)
    void tick() {
      invocations.incrementAndGet();
    }

    int invocations() {
      return invocations.get();
    }
  }
}
