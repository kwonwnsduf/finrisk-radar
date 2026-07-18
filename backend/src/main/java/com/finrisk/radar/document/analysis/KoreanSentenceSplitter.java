package com.finrisk.radar.document.analysis;

import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Component;

@Component
public class KoreanSentenceSplitter {
  private static final Pattern P = Pattern.compile("[^\\n.!?]+(?:[.!?]+|$)");

  public List<Sentence> split(String content) {
    List<Sentence> out = new ArrayList<>();
    Matcher m = P.matcher(content == null ? "" : content);
    int i = 0;
    while (m.find()) {
      String text = m.group().trim();
      if (!text.isEmpty()) out.add(new Sentence(i++, text, m.start(), m.end()));
    }
    return out;
  }

  public record Sentence(int index, String text, int documentStart, int documentEnd) {}
}
