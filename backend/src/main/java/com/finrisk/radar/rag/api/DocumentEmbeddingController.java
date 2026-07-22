package com.finrisk.radar.rag.api;

import com.finrisk.radar.global.error.*;
import com.finrisk.radar.global.response.ApiResponse;
import com.finrisk.radar.rag.service.EmbeddingJobService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents")
public class DocumentEmbeddingController {
  private final EmbeddingJobService jobs;

  public DocumentEmbeddingController(EmbeddingJobService jobs) {
    this.jobs = jobs;
  }

  @GetMapping("/{documentId}/embedding-status")
  public ApiResponse<EmbeddingJobResponse> status(@PathVariable Long documentId) {
    return ApiResponse.success(
        jobs.latest(documentId)
            .map(EmbeddingJobResponse::from)
            .orElseThrow(() -> new BusinessException(ErrorCode.RAG_EMBEDDING_JOB_NOT_FOUND)));
  }
}
