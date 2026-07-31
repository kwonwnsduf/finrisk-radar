package com.finrisk.radar.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.finrisk.radar.notification.NotificationService.Command;
import com.finrisk.radar.user.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {
  private final NotificationRepository repository = mock(NotificationRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final NotificationService service = new NotificationService(repository, users, meters);

  @Test
  void createsOnceAndTreatsConflictAsDuplicate() {
    Command command = command("backtest:completed:one");
    when(repository.insertIgnoringDuplicate(
            anyLong(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any()))
        .thenReturn(1, 0);

    assertThat(service.create(1L, command)).isTrue();
    assertThat(service.create(1L, command)).isFalse();

    assertThat(meters.counter("notification.created", "type", "BACKTEST_COMPLETED").count())
        .isEqualTo(1);
    assertThat(meters.counter("notification.duplicate", "type", "BACKTEST_COMPLETED").count())
        .isEqualTo(1);
  }

  @Test
  void fansTheSameEventOutToEveryAdmin() {
    when(users.findIdsByRole(Role.ROLE_ADMIN)).thenReturn(List.of(10L, 20L));
    when(repository.insertIgnoringDuplicate(
            anyLong(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any()))
        .thenReturn(1);

    assertThat(service.createForAdmins(command("fsd:review-required:7"))).isEqualTo(2);
    verify(repository)
        .insertIgnoringDuplicate(
            eq(10L),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            eq("fsd:review-required:7"),
            any());
    verify(repository)
        .insertIgnoringDuplicate(
            eq(20L),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            eq("fsd:review-required:7"),
            any());
  }

  @Test
  void readsOnlyARecordOwnedByTheAuthenticatedUser() {
    Notification notification = mock(Notification.class);
    when(repository.findByIdAndUserId(9L, 3L)).thenReturn(Optional.of(notification));

    service.markRead(3L, 9L);

    verify(repository).findByIdAndUserId(9L, 3L);
    verify(notification).markRead(any());
  }

  @Test
  void hidesAnotherUsersNotificationAsNotFound() {
    when(repository.findByIdAndUserId(9L, 3L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.markRead(3L, 9L))
        .isInstanceOf(com.finrisk.radar.global.error.BusinessException.class)
        .hasMessageContaining("Notification was not found");
  }

  private Command command(String eventId) {
    return new Command(
        eventId,
        NotificationType.BACKTEST_COMPLETED,
        "title",
        "message",
        NotificationReferenceType.BACKTEST,
        "one",
        "/backtests?jobId=one");
  }
}
