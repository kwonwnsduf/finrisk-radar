package com.finrisk.radar.subscription;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.payment.PaymentOrder;
import com.finrisk.radar.payment.PaymentOrderLookupService;
import com.finrisk.radar.payment.outbox.OutboxService;
import com.finrisk.radar.user.User;
import com.finrisk.radar.user.UserRepository;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SubscriptionServiceTest {
  private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
  private final SubscriptionEntitlementRepository entitlements =
      mock(SubscriptionEntitlementRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final PaymentOrderLookupService paymentOrders = mock(PaymentOrderLookupService.class);
  private final OutboxService outbox = mock(OutboxService.class);
  private final List<SubscriptionEntitlement> ledger = new ArrayList<>();
  private SubscriptionService service;
  private Subscription subscription;
  private User user;

  @BeforeEach
  void setUp() {
    subscription = Subscription.create(7L);
    ReflectionTestUtils.setField(subscription, "id", 11L);
    user = User.create("payer@example.com", "encoded", "Payer");
    ReflectionTestUtils.setField(user, "id", 7L);
    when(subscriptions.findByUserIdForUpdate(7L)).thenReturn(Optional.of(subscription));
    when(entitlements.findByUserIdForUpdate(7L)).thenAnswer(ignored -> new ArrayList<>(ledger));
    when(entitlements.save(any(SubscriptionEntitlement.class)))
        .thenAnswer(
            invocation -> {
              SubscriptionEntitlement value = invocation.getArgument(0);
              ReflectionTestUtils.setField(value, "id", (long) ledger.size() + 1);
              ledger.add(value);
              return value;
            });
    when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
    service = new SubscriptionService(subscriptions, entitlements, users, paymentOrders, outbox);
  }

  @Test
  void cancelingPartiallyUsedContributionKeepsUsedTimeAndPullsLaterContributionForward() {
    LocalDateTime approvedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
    PaymentOrder first = order(101L);
    PaymentOrder second = order(102L);
    service.activate(first, approvedAt);
    service.activate(second, approvedAt.plusDays(1));

    SubscriptionService.EntitlementChange change =
        service.cancelContribution(first, approvedAt.plusDays(10));

    assertThat(change.removedUnusedSeconds()).isEqualTo(20L * 24 * 60 * 60);
    assertThat(ledger.get(0).getStatus()).isEqualTo(EntitlementStatus.CANCELED);
    assertThat(ledger.get(0).getUsedUntil()).isEqualTo(approvedAt.plusDays(10));
    assertThat(ledger.get(1).getPeriodStart()).isEqualTo(approvedAt.plusDays(10));
    assertThat(ledger.get(1).getPeriodEnd()).isEqualTo(approvedAt.plusDays(40));
    assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(approvedAt.plusDays(40));
    assertThat(user.getPlan()).isEqualTo(PlanType.PREMIUM);
  }

  @Test
  void fullyConsumedContributionCannotBeCanceled() {
    LocalDateTime approvedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
    PaymentOrder order = order(101L);
    service.activate(order, approvedAt);

    assertThatThrownBy(() -> service.cancelContribution(order, approvedAt.plusDays(30)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_CANCEL_NOT_ELIGIBLE));
  }

  @Test
  void replayedCancellationDoesNotShiftTheLedgerTwice() {
    LocalDateTime approvedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
    PaymentOrder first = order(101L);
    PaymentOrder second = order(102L);
    service.activate(first, approvedAt);
    service.activate(second, approvedAt);
    LocalDateTime cancellationTime = approvedAt.plusDays(10);
    service.cancelContribution(first, cancellationTime);
    LocalDateTime shiftedEnd = ledger.get(1).getPeriodEnd();

    SubscriptionService.EntitlementChange replay =
        service.cancelContribution(first, cancellationTime.plusMinutes(1));

    assertThat(replay.removedUnusedSeconds()).isZero();
    assertThat(ledger.get(1).getPeriodEnd()).isEqualTo(shiftedEnd);
  }

  private PaymentOrder order(Long id) {
    PaymentOrder order = PaymentOrder.premium(7L, "fr_" + id, UUID.randomUUID().toString());
    ReflectionTestUtils.setField(order, "id", id);
    return order;
  }
}
