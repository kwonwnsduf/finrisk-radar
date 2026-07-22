package com.finrisk.radar.document;

public enum DocumentContentScope {
  FULL_TEXT,
  PARTIAL,
  SNIPPET,
  UNUSABLE;

  public static DocumentContentScope forExtractedText(String title, String content) {
    String normalizedTitle = normalize(title);
    String normalizedContent = normalize(content);
    if (normalizedContent.length() < 100 || normalizedContent.equals(normalizedTitle)) {
      return UNUSABLE;
    }
    return normalizedContent.length() >= 500 ? FULL_TEXT : PARTIAL;
  }

  public boolean embeddable() {
    return this != UNUSABLE;
  }

  private static String normalize(String value) {
    return value == null ? "" : value.replaceAll("\\s+", " ").trim();
  }
}
