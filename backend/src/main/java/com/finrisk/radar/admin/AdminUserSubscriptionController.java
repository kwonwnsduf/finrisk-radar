package com.finrisk.radar.admin;

import com.finrisk.radar.global.response.ApiResponse;
import com.finrisk.radar.subscription.*;
import com.finrisk.radar.user.Role;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin users and subscriptions")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserSubscriptionController {
  private final AdminUserSubscriptionQueryService service;

  public AdminUserSubscriptionController(AdminUserSubscriptionQueryService service) {
    this.service = service;
  }

  @GetMapping("/users")
  public ApiResponse<AdminPage<AdminUserItem>> users(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) PlanType plan,
      @RequestParam(required = false) Role role,
      @RequestParam(required = false) LocalDateTime joinedFrom,
      @RequestParam(required = false) LocalDateTime joinedTo,
      @RequestParam(required = false) Boolean activeSubscription,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(
        service.users(
            search, plan, role, joinedFrom, joinedTo, activeSubscription, page, size));
  }

  @GetMapping("/subscriptions")
  public ApiResponse<AdminPage<AdminSubscriptionItem>> subscriptions(
      @RequestParam(required = false) PlanType plan,
      @RequestParam(required = false) SubscriptionStatus status,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) Boolean expiring,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(service.subscriptions(plan, status, userId, expiring, page, size));
  }
}
