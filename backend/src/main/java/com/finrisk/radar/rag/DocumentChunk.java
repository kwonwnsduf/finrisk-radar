package com.finrisk.radar.rag;

public record DocumentChunk(
    int chunkIndex,
    int sentenceStartIndex,
    int sentenceEndIndex,
    String content,
    String contentHash) {}
