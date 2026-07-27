package com.finrisk.radar.admin;

import com.finrisk.radar.payment.*;
import com.finrisk.radar.subscription.*;
import com.finrisk.radar.user.*;
import jakarta.persistence.criteria.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserSubscriptionQueryService {
  private final UserRepository users;
  private final SubscriptionRepository subscriptions;
  private final PaymentOrderRepository orders;

  public AdminUserSubscriptionQueryService(
      UserRepository users,
      SubscriptionRepository subscriptions,
      PaymentOrderRepository orders) {
    this.users = users;
    this.subscriptions = subscriptions;
    this.orders = orders;
  }

  @Transactional(readOnly = true)
  public AdminPage<AdminUserItem> users(
      String search,
      PlanType plan,
      Role role,
      LocalDateTime from,
      LocalDateTime to,
      Boolean active,
      int page,
      int size) {
    LocalDateTime now = LocalDateTime.now();
    Specification<User> specification =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (search != null && !search.isBlank()) {
            String keyword = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            predicates.add(
                cb.or(
                    cb.like(cb.lower(root.get("email")), keyword),
                    cb.like(cb.lower(root.get("name")), keyword)));
          }
          if (plan != null) predicates.add(cb.equal(root.get("plan"), plan));
          if (role != null) predicates.add(cb.equal(root.get("role"), role));
          if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
          if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
          if (active != null) {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<Subscription> subscription = subquery.from(Subscription.class);
            subquery
                .select(subscription.get("userId"))
                .where(
                    cb.equal(subscription.get("userId"), root.get("id")),
                    cb.equal(subscription.get("status"), SubscriptionStatus.ACTIVE),
                    cb.greaterThan(subscription.get("currentPeriodEnd"), now));
            predicates.add(active ? cb.exists(subquery) : cb.not(cb.exists(subquery)));
          }
          return cb.and(predicates.toArray(Predicate[]::new));
        };
    Page<User> result =
        users.findAll(
            specification,
            PageRequest.of(safePage(page), safeSize(size), Sort.by("createdAt").descending()));
    Set<Long> userIds = result.stream().map(User::getId).collect(Collectors.toSet());
    Map<Long, Subscription> subscriptionByUser =
        subscriptions.findByUserIdIn(userIds).stream()
            .collect(Collectors.toMap(Subscription::getUserId, Function.identity()));
    Map<Long, PaymentOrder> latestOrderByUser =
        orders.findLatestByUserIds(userIds).stream()
            .collect(
                Collectors.toMap(
                    PaymentOrder::getUserId,
                    Function.identity(),
                    (left, right) ->
                        left.getCreatedAt().isAfter(right.getCreatedAt()) ? left : right));
    List<AdminUserItem> items =
        result.stream()
            .map(
                user -> {
                  Subscription subscription = subscriptionByUser.get(user.getId());
                  PaymentOrder order = latestOrderByUser.get(user.getId());
                  boolean isActive =
                      subscription != null
                          && subscription.getStatus() == SubscriptionStatus.ACTIVE
                          && subscription.getCurrentPeriodEnd() != null
                          && subscription.getCurrentPeriodEnd().isAfter(now);
                  return new AdminUserItem(
                      user.getId(),
                      user.getEmail(),
                      user.getName(),
                      user.getRole(),
                      user.getPlan(),
                      user.getCreatedAt(),
                      isActive,
                      subscription == null ? null : subscription.getCurrentPeriodEnd(),
                      order == null ? null : order.getOrderId(),
                      order == null ? null : order.getStatus(),
                      order == null ? null : order.getCreatedAt());
                })
            .toList();
    return AdminPage.from(result, items);
  }

  @Transactional(readOnly = true)
  public AdminPage<AdminSubscriptionItem> subscriptions(
      PlanType plan,
      SubscriptionStatus status,
      Long userId,
      Boolean expiring,
      int page,
      int size) {
    LocalDateTime now = LocalDateTime.now();
    Specification<Subscription> specification =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (plan != null) predicates.add(cb.equal(root.get("plan"), plan));
          if (status != null) predicates.add(cb.equal(root.get("status"), status));
          if (userId != null) predicates.add(cb.equal(root.get("userId"), userId));
          if (Boolean.TRUE.equals(expiring)) {
            predicates.add(cb.equal(root.get("status"), SubscriptionStatus.ACTIVE));
            predicates.add(cb.greaterThan(root.get("currentPeriodEnd"), now));
            predicates.add(
                cb.lessThanOrEqualTo(root.get("currentPeriodEnd"), now.plusDays(7)));
          }
          return cb.and(predicates.toArray(Predicate[]::new));
        };
    Page<Subscription> result =
        subscriptions.findAll(
            specification,
            PageRequest.of(
                safePage(page), safeSize(size), Sort.by("currentPeriodEnd").ascending()));
    Map<Long, User> userById =
        users.findAllById(result.stream().map(Subscription::getUserId).toList()).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
    Map<Long, PaymentOrder> orderById =
        orders
            .findByIdIn(
                result.stream()
                    .map(Subscription::getActivatedByPaymentOrderId)
                    .filter(Objects::nonNull)
                    .toList())
            .stream()
            .collect(Collectors.toMap(PaymentOrder::getId, Function.identity()));
    List<AdminSubscriptionItem> items =
        result.stream()
            .map(
                subscription -> {
                  User user = userById.get(subscription.getUserId());
                  PaymentOrder order = orderById.get(subscription.getActivatedByPaymentOrderId());
                  return new AdminSubscriptionItem(
                      subscription.getId(),
                      subscription.getUserId(),
                      user == null ? null : user.getEmail(),
                      user == null ? null : user.getName(),
                      subscription.getPlan(),
                      subscription.getStatus(),
                      subscription.getCurrentPeriodStart(),
                      subscription.getCurrentPeriodEnd(),
                      order == null ? null : order.getOrderId());
                })
            .toList();
    return AdminPage.from(result, items);
  }

  private static int safePage(int page) {
    return Math.max(0, page);
  }

  private static int safeSize(int size) {
    return Math.min(100, Math.max(1, size));
  }
}
