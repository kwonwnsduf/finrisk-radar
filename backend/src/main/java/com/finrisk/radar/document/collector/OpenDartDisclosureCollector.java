package com.finrisk.radar.document.collector;

import com.fasterxml.jackson.databind.*;
import com.finrisk.radar.document.*;
import com.finrisk.radar.financial.DartClient;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

@Component
public class OpenDartDisclosureCollector implements DocumentSourceCollector {
  private final DartClient dart;
  private final ObjectMapper mapper;

  public OpenDartDisclosureCollector(DartClient dart, ObjectMapper mapper) {
    this.dart = dart;
    this.mapper = mapper;
  }

  public boolean supports(DocumentSourceType source) {
    return source == DocumentSourceType.OPEN_DART;
  }

  public List<CollectedDocument> collect(DocumentCollectionContext context) {
    if (context.corpCode() == null)
      throw new IllegalStateException("DART corp code is unavailable.");
    try {
      String raw =
          dart.searchDisclosures(context.corpCode(), context.fromDate(), context.toDate(), 1);
      JsonNode root = mapper.readTree(raw);
      if (!"000".equals(root.path("status").asText())
          && !"013".equals(root.path("status").asText()))
        throw new IllegalStateException(
            "DART disclosure search returned " + root.path("message").asText());
      List<CollectedDocument> result = new ArrayList<>();
      for (JsonNode item : root.path("list")) {
        String receipt = item.path("rcept_no").asText();
        byte[] zip = dart.downloadDisclosureDocument(receipt);
        ExtractedSource extracted = extract(zip);
        String title = item.path("report_nm").asText();
        LocalDateTime date =
            LocalDate.parse(item.path("rcept_dt").asText(), DateTimeFormatter.BASIC_ISO_DATE)
                .atStartOfDay();
        String url = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + receipt;
        result.add(
            new CollectedDocument(
                DocumentType.DISCLOSURE,
                DocumentSourceType.OPEN_DART,
                "OpenDART",
                title,
                extracted.text(),
                null,
                url,
                receipt,
                date,
                DocumentContentScope.forExtractedText(title, extracted.text()),
                zip,
                "application/zip",
                "zip",
                extracted.payload(),
                extracted.contentType(),
                extracted.extension(),
                context.corpCode()));
      }
      return result;
    } catch (IOException e) {
      throw new IllegalStateException("DART disclosure response is malformed.", e);
    }
  }

  static ExtractedSource extract(byte[] payload) {
    if (payload == null || payload.length == 0)
      throw new IllegalStateException("DART document is empty.");
    ExtractedSource best = null;
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(payload))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory()) continue;
        String name = entry.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".xml") || name.endsWith(".html") || name.endsWith(".htm")) {
          byte[] bytes = zip.readAllBytes();
          String raw = new String(bytes, StandardCharsets.UTF_8);
          String text = Jsoup.parse(raw).text();
          String extension = name.endsWith(".xml") ? "xml" : "html";
          String type = extension.equals("xml") ? "application/xml" : "text/html";
          if (best == null || text.length() > best.text().length())
            best = new ExtractedSource(text, bytes, type, extension);
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("DART document ZIP could not be read.", e);
    }
    if (best == null) throw new IllegalStateException("DART document contained no XML/HTML text.");
    return best;
  }

  record ExtractedSource(String text, byte[] payload, String contentType, String extension) {}
}
