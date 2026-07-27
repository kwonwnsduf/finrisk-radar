package com.finrisk.radar.admin;

import com.finrisk.radar.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/dashboard")
@Tag(name = "Admin operations")
@SecurityRequirement(name = "bearerAuth")
public class AdminDashboardController {
  private final AdminDashboardQueryService service;

  public AdminDashboardController(AdminDashboardQueryService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Get the SaaS operations dashboard")
  public ApiResponse<AdminDashboardResponse> get() {
    return ApiResponse.success(service.get());
  }
}
