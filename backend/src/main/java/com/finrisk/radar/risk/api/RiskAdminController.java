package com.finrisk.radar.risk.api;

import com.finrisk.radar.global.response.ApiResponse;
import com.finrisk.radar.risk.*;
import com.finrisk.radar.risk.service.RiskAdminService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/risks")
@SecurityRequirement(name = "bearerAuth")
public class RiskAdminController {
  private final RiskAdminService admin;

  public RiskAdminController(RiskAdminService a) {
    admin = a;
  }

  @PostMapping("/assets/{assetId}/credit-events")
  public ResponseEntity<ApiResponse<CreditEvent>> event(
      @PathVariable Long assetId, @Valid @RequestBody CreditEventCreateRequest r) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(admin.createEvent(assetId, r)));
  }

  @GetMapping("/assets/{assetId}/credit-events")
  public ApiResponse<List<CreditEvent>> events(@PathVariable Long assetId) {
    return ApiResponse.success(admin.events(assetId));
  }

  @PostMapping("/asset-relationships")
  public ResponseEntity<ApiResponse<AssetRelationship>> relationship(
      @Valid @RequestBody AssetRelationshipCreateRequest r) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(admin.relationship(r)));
  }
}
