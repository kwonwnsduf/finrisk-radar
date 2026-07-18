package com.finrisk.radar.document.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

@Component
public class DocumentContentNormalizer {
  public String text(String value) {
    return Jsoup.parse(value == null ? "" : value).text().replaceAll("\\s+", " ").trim();
  }

  public String url(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      URI u = URI.create(value.trim());
      return new URI(u.getScheme(), u.getAuthority(), u.getPath(), u.getQuery(), null).toString();
    } catch (Exception e) {
      return value.trim();
    }
  }

  public String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
