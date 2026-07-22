package com.finrisk.radar.rag.chunk;

import com.finrisk.radar.document.analysis.KoreanSentenceSplitter;
import com.finrisk.radar.document.service.DocumentContentNormalizer;
import com.finrisk.radar.rag.DocumentChunk;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class DocumentChunker {
  private static final int MIN_TARGET = 500;
  private static final int TARGET = 800;
  private static final int MAX = 1000;

  private final KoreanSentenceSplitter splitter;
  private final DocumentContentNormalizer normalizer;

  public DocumentChunker(KoreanSentenceSplitter splitter, DocumentContentNormalizer normalizer) {
    this.splitter = splitter;
    this.normalizer = normalizer;
  }

  public List<DocumentChunk> chunk(String content) {
    List<Segment> segments = expand(splitter.split(content));
    if (segments.isEmpty()) return List.of();
    List<DocumentChunk> chunks = new ArrayList<>();
    int cursor = 0;
    Segment overlap = null;
    while (cursor < segments.size()) {
      List<Segment> selected = new ArrayList<>();
      Segment next = segments.get(cursor);
      if (overlap != null && joinedLength(List.of(overlap, next)) <= MAX) selected.add(overlap);
      int firstNew = cursor;
      while (cursor < segments.size()) {
        Segment candidate = segments.get(cursor);
        int proposed = joinedLengthWith(selected, candidate);
        int current = joinedLength(selected);
        if (!selected.isEmpty() && proposed > MAX) break;
        if (cursor > firstNew && current >= MIN_TARGET && proposed > TARGET) break;
        selected.add(candidate);
        cursor++;
      }
      if (cursor == firstNew) {
        selected.clear();
        selected.add(segments.get(cursor++));
      }
      String text = selected.stream().map(Segment::text).reduce((a, b) -> a + " " + b).orElse("");
      Segment first = selected.get(0), last = selected.get(selected.size() - 1);
      chunks.add(
          new DocumentChunk(
              chunks.size(),
              first.sentenceIndex(),
              last.sentenceIndex(),
              text,
              normalizer.hash(text)));
      overlap = segments.get(cursor - 1);
    }
    return List.copyOf(chunks);
  }

  public String embeddingInput(String title, DocumentChunk chunk) {
    return "제목: " + normalizer.text(title) + "\n\n본문:\n" + chunk.content();
  }

  private List<Segment> expand(List<KoreanSentenceSplitter.Sentence> sentences) {
    List<Segment> out = new ArrayList<>();
    for (var sentence : sentences) {
      String remaining = normalizer.text(sentence.text());
      while (remaining.length() > MAX) {
        int cut = remaining.lastIndexOf(' ', TARGET);
        if (cut <= 0) cut = TARGET;
        out.add(new Segment(sentence.index(), remaining.substring(0, cut).trim()));
        remaining = remaining.substring(cut).trim();
      }
      if (!remaining.isBlank()) out.add(new Segment(sentence.index(), remaining));
    }
    return out;
  }

  private int joinedLengthWith(List<Segment> values, Segment additional) {
    return joinedLength(values) + (values.isEmpty() ? 0 : 1) + additional.text().length();
  }

  private int joinedLength(List<Segment> values) {
    return values.stream().mapToInt(s -> s.text().length()).sum() + Math.max(0, values.size() - 1);
  }

  private record Segment(int sentenceIndex, String text) {}
}
