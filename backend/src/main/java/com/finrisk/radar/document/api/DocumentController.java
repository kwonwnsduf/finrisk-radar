package com.finrisk.radar.document.api;

import com.finrisk.radar.document.DocumentSourceType;
import com.finrisk.radar.document.DocumentType;
import com.finrisk.radar.document.service.DocumentQueryService;
import com.finrisk.radar.global.response.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
  private final DocumentQueryService queries;

  public DocumentController(DocumentQueryService queries) {
    this.queries = queries;
  }

  @GetMapping
  public ApiResponse<List<DocumentResponse>> list(
      @RequestParam(required = false) Long assetId,
      @RequestParam(required = false) DocumentSourceType sourceType,
      @RequestParam(required = false) DocumentType documentType,
      @RequestParam(required = false) Boolean riskOnly,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) Long cursor,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(
        queries.list(assetId, sourceType, documentType, riskOnly, from, to, cursor, size));
  }

  @GetMapping("/{id}")
  public ApiResponse<DocumentResponse> get(@PathVariable Long id) {
    return ApiResponse.success(queries.get(id));
  }

  @GetMapping("/{id}/risk-matches")
  public ApiResponse<List<DocumentRiskMatchResponse>> matches(@PathVariable Long id) {
    return ApiResponse.success(queries.matchResponses(id));
  }
}
