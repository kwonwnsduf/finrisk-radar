package com.finrisk.radar.rag.api;

import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.document.*;
import com.finrisk.radar.global.error.*;
import com.finrisk.radar.global.response.ApiResponse;
import com.finrisk.radar.rag.service.EmbeddingRequestService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class RagAdminController {
  private final DocumentRepository documents;
  private final AssetRepository assets;
  private final EmbeddingRequestService requests;

  public RagAdminController(
      DocumentRepository documents, AssetRepository assets, EmbeddingRequestService requests) {
    this.documents = documents;
    this.assets = assets;
    this.requests = requests;
  }

  @PostMapping("/documents/{documentId}/embedding")
  public ResponseEntity<ApiResponse<EmbeddingJobResponse>> embed(@PathVariable Long documentId) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            ApiResponse.success(
                EmbeddingJobResponse.from(requests.request(documentId, UUID.randomUUID(), true))));
  }

  @PostMapping("/document-embeddings/rebuild")
  public ResponseEntity<ApiResponse<EmbeddingRebuildResponse>> rebuild(
      @Valid @RequestBody EmbeddingRebuildRequest request) {
    List<Document> candidates = candidates(request);
    List<EmbeddingJobResponse> jobs =
        request.resolvedDryRun()
            ? List.of()
            : candidates.stream()
                .map(
                    document ->
                        EmbeddingJobResponse.from(
                            requests.request(document.getId(), UUID.randomUUID(), true)))
                .toList();
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            ApiResponse.success(
                new EmbeddingRebuildResponse(
                    request.resolvedDryRun(),
                    candidates.size(),
                    candidates.stream().map(Document::getId).toList(),
                    jobs)));
  }

  private List<Document> candidates(EmbeddingRebuildRequest request) {
    if (request.assetId() != null && !assets.existsById(request.assetId())) {
      throw new BusinessException(ErrorCode.ASSET_NOT_FOUND);
    }
    if (request.documentIds() != null && !request.documentIds().isEmpty()) {
      Map<Long, Document> found = new HashMap<>();
      documents
          .findAllById(request.documentIds())
          .forEach(document -> found.put(document.getId(), document));
      return request.documentIds().stream().map(found::get).filter(Objects::nonNull).toList();
    }
    return documents.findEmbeddingRebuildCandidates(
        request.sourceType(),
        request.publishedFrom() == null ? null : request.publishedFrom().atStartOfDay(),
        request.publishedTo() == null ? null : request.publishedTo().plusDays(1).atStartOfDay(),
        request.assetId(),
        PageRequest.of(0, 100));
  }
}
