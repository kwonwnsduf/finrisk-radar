package com.finrisk.radar.rag.service;

import com.finrisk.radar.asset.*;
import com.finrisk.radar.document.*;
import com.finrisk.radar.global.error.*;
import com.finrisk.radar.rag.*;
import com.finrisk.radar.rag.api.*;
import com.finrisk.radar.rag.embedding.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RagSearchService {
  private final RagVectorSearchRepository vectors;
  private final EmbeddingClient embeddings;
  private final AssetRepository assets;
  private final DocumentAssetMappingRepository mappings;
  private final DocumentRiskMatchRepository matches;
  private final Counter requests;
  private final Timer duration;

  public RagSearchService(
      RagVectorSearchRepository vectors,
      EmbeddingClient embeddings,
      AssetRepository assets,
      DocumentAssetMappingRepository mappings,
      DocumentRiskMatchRepository matches,
      MeterRegistry meters) {
    this.vectors = vectors;
    this.embeddings = embeddings;
    this.assets = assets;
    this.mappings = mappings;
    this.matches = matches;
    requests = meters.counter("rag.search.requests");
    duration = meters.timer("rag.search.duration");
  }

  public List<RagSearchResponse> search(RagSearchRequest request) {
    Timer.Sample sample = Timer.start();
    requests.increment();
    try {
      if (request.assetId() != null && !assets.existsById(request.assetId())) {
        throw new BusinessException(ErrorCode.ASSET_NOT_FOUND);
      }
      float[] query;
      try {
        query = embeddings.embed(request.query().trim());
      } catch (EmbeddingClientException exception) {
        throw new BusinessException(ErrorCode.RAG_EMBEDDING_UNAVAILABLE);
      }
      List<RagSearchHit> hits =
          vectors.search(
              query,
              embeddings.modelName(),
              new RagSearchCriteria(
                  request.assetId(),
                  request.documentType(),
                  request.sourceType(),
                  request.publishedFrom(),
                  request.publishedTo(),
                  request.resolvedLimit(),
                  request.minimumSimilarity()));
      if (hits.isEmpty()) return List.of();
      Set<Long> documentIds =
          hits.stream().map(RagSearchHit::documentId).collect(Collectors.toSet());
      Map<Long, Asset> assetsById =
          assets
              .findAllById(
                  mappings.findByDocumentIdIn(documentIds).stream()
                      .map(DocumentAssetMapping::getAssetId)
                      .collect(Collectors.toSet()))
              .stream()
              .collect(Collectors.toMap(Asset::getId, Function.identity()));
      Map<Long, List<RagAssetResponse>> assetsByDocument =
          mappings.findByDocumentIdIn(documentIds).stream()
              .filter(mapping -> assetsById.containsKey(mapping.getAssetId()))
              .collect(
                  Collectors.groupingBy(
                      DocumentAssetMapping::getDocumentId,
                      Collectors.mapping(
                          mapping ->
                              new RagAssetResponse(
                                  mapping.getAssetId(),
                                  assetsById.get(mapping.getAssetId()).getName()),
                          Collectors.toList())));
      Map<Long, List<DocumentRiskMatch>> matchesByDocument =
          matches.findByDocumentIdIn(documentIds).stream()
              .collect(Collectors.groupingBy(DocumentRiskMatch::getDocumentId));
      return hits.stream()
          .map(
              hit ->
                  RagSearchResponse.from(
                      hit,
                      assetsByDocument.getOrDefault(hit.documentId(), List.of()),
                      matchesByDocument.getOrDefault(hit.documentId(), List.of()).stream()
                          .filter(
                              match ->
                                  match.getSentenceIndex() >= hit.sentenceStartIndex()
                                      && match.getSentenceIndex() <= hit.sentenceEndIndex())
                          .map(RagRiskMatchResponse::from)
                          .toList()))
          .toList();
    } finally {
      sample.stop(duration);
    }
  }
}
