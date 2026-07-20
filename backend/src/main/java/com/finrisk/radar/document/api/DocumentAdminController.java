package com.finrisk.radar.document.api;

import com.finrisk.radar.auth.jwt.CustomUserPrincipal;
import com.finrisk.radar.document.*;
import com.finrisk.radar.document.service.*;
import com.finrisk.radar.global.response.ApiResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class DocumentAdminController {
  private final DocumentCollectionRequestService requests;
  private final DocumentCollectionJobService jobs;
  private final DocumentAssetMappingRepository mappings;
  private final DocumentRepository documents;
  private final com.finrisk.radar.asset.AssetRepository assets;

  public DocumentAdminController(
      DocumentCollectionRequestService requests,
      DocumentCollectionJobService jobs,
      DocumentAssetMappingRepository mappings,
      DocumentRepository documents,
      com.finrisk.radar.asset.AssetRepository assets) {
    this.requests = requests;
    this.jobs = jobs;
    this.mappings = mappings;
    this.documents = documents;
    this.assets = assets;
  }

  @PostMapping("/document-collections")
  public ResponseEntity<ApiResponse<List<DocumentCollectionJobResponse>>> collect(
      @AuthenticationPrincipal CustomUserPrincipal p,
      @Valid @RequestBody DocumentCollectionRequest r) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            ApiResponse.success(
                requests
                    .request(p.userId(), r.assetIds(), r.sourceTypes(), r.fromDate(), r.toDate())
                    .stream()
                    .map(DocumentCollectionJobResponse::from)
                    .toList()));
  }

  @GetMapping("/document-collections/{id}")
  public ApiResponse<DocumentCollectionJobResponse> job(@PathVariable UUID id) {
    return ApiResponse.success(DocumentCollectionJobResponse.from(jobs.get(id)));
  }

  @GetMapping("/document-collections")
  public ApiResponse<List<DocumentCollectionJobResponse>> jobs(
      @RequestParam(required = false) Long assetId) {
    return ApiResponse.success(
        jobs.recent(assetId).stream().map(DocumentCollectionJobResponse::from).toList());
  }

  @PostMapping("/documents/{documentId}/asset-mappings")
  public ApiResponse<Long> map(@PathVariable Long documentId, @RequestParam Long assetId) {
    documents.findById(documentId).orElseThrow();
    var asset = assets.findById(assetId).orElseThrow();
    var existing = mappings.findByDocumentIdAndAssetId(documentId, assetId);
    var m =
        existing.orElseGet(
            () ->
                mappings.save(
                    DocumentAssetMapping.create(
                        documentId,
                        assetId,
                        AssetMatchMethod.MANUAL,
                        asset.getName(),
                        BigDecimal.ONE,
                        false)));
    return ApiResponse.success(m.getId());
  }

  @DeleteMapping("/documents/{documentId}/asset-mappings/{assetId}")
  public ApiResponse<Boolean> unmap(@PathVariable Long documentId, @PathVariable Long assetId) {
    mappings.deleteByDocumentIdAndAssetId(documentId, assetId);
    return ApiResponse.success(true);
  }
}
