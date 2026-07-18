package com.finrisk.radar.document.analysis;

import java.math.*;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Component;

@Component
public class DocumentAmountExtractor {
  private static final String NUMBER = "[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?";
  private static final String CURRENCY = "USD|EUR|JPY|KRW";
  private static final String FOREIGN_UNIT = "K|M|B|million|billion";

  private static final Pattern KRW =
      Pattern.compile("(?<![0-9])([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?)\\s*(조원|억원|만원|원)");
  private static final Pattern FORE =
      Pattern.compile(
          "(?i)(?:("
              + CURRENCY
              + ")\\s*("
              + NUMBER
              + ")\\s*("
              + FOREIGN_UNIT
              + ")?|((?:"
              + NUMBER
              + "))\\s*("
              + FOREIGN_UNIT
              + ")?\\s*("
              + CURRENCY
              + "))");

  public List<ExtractedAmount> extract(String sentence) {
    List<ExtractedAmount> out = new ArrayList<>();
    Matcher k = KRW.matcher(sentence);
    while (k.find()) out.add(amount(k.group(), k.group(1), "KRW", k.group(2), k.start(), k.end()));
    Matcher f = FORE.matcher(sentence);
    while (f.find()) {
      String currency = f.group(1) != null ? f.group(1) : f.group(6);
      String number = f.group(2) != null ? f.group(2) : f.group(4);
      String unit = f.group(3) != null ? f.group(3) : f.group(5);
      out.add(
          amount(
              f.group(),
              number,
              currency.toUpperCase(Locale.ROOT),
              unit,
              kSafe(f.start()),
              f.end()));
    }
    return out.stream().sorted(Comparator.comparingInt(ExtractedAmount::start)).toList();
  }

  public ExtractedAmount nearest(List<ExtractedAmount> values, int keywordStart) {
    ExtractedAmount following =
        values.stream()
            .filter(v -> v.start() >= keywordStart && v.start() - keywordStart <= 40)
            .min(Comparator.comparingInt(v -> v.start() - keywordStart))
            .orElse(null);
    return following != null
        ? following
        : values.stream().min(Comparator.comparingInt(v -> distance(v, keywordStart))).orElse(null);
  }

  private int distance(ExtractedAmount v, int pos) {
    return pos < v.start() ? v.start() - pos : pos > v.end() ? pos - v.end() : 0;
  }

  private ExtractedAmount amount(
      String raw, String number, String currency, String unit, int start, int end) {
    BigDecimal value = new BigDecimal(number.replace(",", ""));
    BigDecimal multiplier =
        switch (unit == null ? "" : unit.toLowerCase(Locale.ROOT)) {
          case "조원" -> new BigDecimal("1000000000000");
          case "억원" -> new BigDecimal("100000000");
          case "만원" -> new BigDecimal("10000");
          case "k" -> new BigDecimal("1000");
          case "m", "million" -> new BigDecimal("1000000");
          case "b", "billion" -> new BigDecimal("1000000000");
          default -> BigDecimal.ONE;
        };
    return new ExtractedAmount(
        value.multiply(multiplier).setScale(2, RoundingMode.HALF_UP), currency, raw, start, end);
  }

  private int kSafe(int value) {
    return value;
  }

  public record ExtractedAmount(
      BigDecimal amount, String currency, String originalText, int start, int end) {}
}
