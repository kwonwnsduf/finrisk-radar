package com.finrisk.radar.rag.embedding;

import java.util.List;

public interface EmbeddingClient {
  float[] embed(String text);

  List<float[]> embedAll(List<String> texts);

  String modelName();

  int dimensions();
}
