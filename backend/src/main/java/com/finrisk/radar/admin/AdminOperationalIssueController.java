package com.finrisk.radar.admin;

import com.finrisk.radar.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/operational-issues")
@Tag(name = "Admin operational issues")
@SecurityRequirement(name = "bearerAuth")
public class AdminOperationalIssueController {
  private final AdminOperationalIssueQueryService service;

  public AdminOperationalIssueController(AdminOperationalIssueQueryService service) {
    this.service = service;
  }

  @GetMapping("/backtests")
  public ApiResponse<AdminPage<AdminOperationalIssue>> backtests(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(service.backtests(page, size));
  }

  @GetMapping("/reports")
  public ApiResponse<AdminPage<AdminOperationalIssue>> reports(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(service.reports(page, size));
  }

  @GetMapping("/collections")
  public ApiResponse<AdminPage<AdminOperationalIssue>> collections(
      @RequestParam(defaultValue = "MARKET_DATA") CollectionIssueKind kind,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(service.collections(kind, page, size));
  }
}
