package com.finrisk.radar.rag.api;

import com.finrisk.radar.global.response.ApiResponse;
import com.finrisk.radar.rag.service.RagSearchService;
import com.finrisk.radar.usage.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag")
public class RagController {
  private final RagSearchService search;

  public RagController(RagSearchService search) {
    this.search = search;
  }

  @PostMapping("/search")
  @UsageLimit(UsageType.RAG_SEARCH)
  public ApiResponse<List<RagSearchResponse>> search(@Valid @RequestBody RagSearchRequest request) {
    return ApiResponse.success(search.search(request));
  }
}
