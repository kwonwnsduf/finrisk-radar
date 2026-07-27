package com.finrisk.radar.admin;

import com.finrisk.radar.fsd.FsdStatus;
import com.finrisk.radar.global.response.ApiResponse;
import com.finrisk.radar.payment.PaymentOrderStatus;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/payments")
@Tag(name = "Admin payments")
@SecurityRequirement(name = "bearerAuth")
public class AdminPaymentController {
  private final AdminPaymentQueryService service;

  public AdminPaymentController(AdminPaymentQueryService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<AdminPage<AdminPaymentItem>> list(
      @RequestParam(required = false) String orderId,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) String email,
      @RequestParam(required = false) PaymentOrderStatus status,
      @RequestParam(required = false) LocalDateTime from,
      @RequestParam(required = false) LocalDateTime to,
      @RequestParam(required = false) FsdStatus fsdStatus,
      @RequestParam(required = false) Boolean recoveryRequired,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(
        service.list(
            orderId, userId, email, status, from, to, fsdStatus, recoveryRequired, page, size));
  }

  @GetMapping("/{orderId}")
  public ApiResponse<AdminPaymentDetail> get(@PathVariable String orderId) {
    return ApiResponse.success(service.get(orderId));
  }
}
