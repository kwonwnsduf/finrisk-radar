package com.finrisk.radar.fsd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FsdEventReviewTest {

  @Test
  void openEventCanMoveToReviewingAndThenResolved() {
    FsdEvent event = event();

    event.review(FsdStatus.REVIEWING, "checking", 42L);
    event.review(FsdStatus.RESOLVED, "confirmed", 42L);

    assertThat(event.getStatus()).isEqualTo(FsdStatus.RESOLVED);
    assertThat(event.getReviewedBy()).isEqualTo(42L);
    assertThat(event.getReviewNote()).isEqualTo("confirmed");
  }

  @Test
  void sameBackwardAndTerminalTransitionsAreRejected() {
    FsdEvent reviewing = event();
    reviewing.review(FsdStatus.REVIEWING, "checking", 42L);

    assertThatThrownBy(() -> reviewing.review(FsdStatus.REVIEWING, "again", 42L))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> reviewing.review(FsdStatus.OPEN, "back", 42L))
        .isInstanceOf(IllegalStateException.class);

    reviewing.review(FsdStatus.FALSE_POSITIVE, "done", 42L);
    assertThatThrownBy(() -> reviewing.review(FsdStatus.RESOLVED, "change", 42L))
        .isInstanceOf(IllegalStateException.class);
  }

  private static FsdEvent event() {
    return FsdEvent.detected(
        1L,
        2L,
        3L,
        FsdPhase.POST_PAYMENT,
        new RuleResult(
            "VELOCITY",
            FsdDecision.REVIEW,
            FsdSeverity.HIGH,
            80,
            "suspicious",
            Map.of("attempts", 5)));
  }
}
