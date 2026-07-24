package com.finrisk.radar.backtest.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finrisk.radar.asset.*;
import com.finrisk.radar.backtest.*;
import com.finrisk.radar.backtest.api.*;
import com.finrisk.radar.report.llm.*;
import com.finrisk.radar.usage.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;

class NaturalLanguageBacktestServiceTest {
  private LlmClient llm;
  private AssetRepository assets;
  private BacktestRequestService requests;
  private BacktestJobService jobs;
  private UsageLimitService usage;
  private NaturalLanguageBacktestService service;
  private Asset samsung;

  @BeforeEach
  void setUp() {
    llm = mock(LlmClient.class);
    assets = mock(AssetRepository.class);
    requests = mock(BacktestRequestService.class);
    jobs = mock(BacktestJobService.class);
    usage = mock(UsageLimitService.class);
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    service =
        new NaturalLanguageBacktestService(
            llm, mapper, assets, new BacktestRequestValidator(), requests, jobs, usage);
    samsung = Asset.create("삼성전자", "005930", "KOSPI", null, "KR", "KRW", AssetType.STOCK);
    setId(samsung, 1L);
    when(llm.configured()).thenReturn(true);
  }

  @Test
  void clarificationDoesNotConsumeAnyUsage() {
    when(llm.generate(any()))
        .thenReturn(
            new LlmResponse(
                """
                {
                  "assetQuery": null,
                  "strategyType": null,
                  "startDate": null,
                  "endDate": null,
                  "initialCash": null,
                  "buyConditions": [],
                  "sellConditions": [],
                  "missingFields": ["asset", "strategyType", "startDate", "endDate"]
                }
                """,
                "model",
                new LlmUsage(10, 5)));
    var response = service.request(1L, new NaturalLanguageBacktestRequest("삼성전자 백테스트", null));
    assertThat(response.outcome()).isEqualTo("NEEDS_CLARIFICATION");
    verifyNoInteractions(usage);
    verifyNoInteractions(requests);
  }

  @Test
  void consumesBacktestOnceOnlyAfterJobWasCreated() {
    when(llm.generate(any()))
        .thenReturn(
            new LlmResponse(
                """
                {
                  "assetQuery": "삼성전자",
                  "strategyType": "BUY_AND_HOLD",
                  "startDate": "2024-01-01",
                  "endDate": "2024-12-31",
                  "initialCash": 10000000,
                  "buyConditions": [],
                  "sellConditions": [],
                  "missingFields": []
                }
                """,
                "model",
                new LlmUsage(10, 5)));
    when(assets.search("삼성전자", null)).thenReturn(List.of(samsung));
    var reservation = new UsageLimitService.UsageReservation("usage:key");
    when(usage.reserve(1L, UsageType.BACKTEST)).thenReturn(reservation);
    doAnswer(
            inv -> {
              @SuppressWarnings("unchecked")
              Consumer<BacktestJob> callback = inv.getArgument(2);
              BacktestJob job =
                  BacktestJob.requested(
                      1L,
                      1L,
                      StrategyType.BUY_AND_HOLD,
                      java.time.LocalDate.of(2024, 1, 1),
                      java.time.LocalDate.of(2024, 12, 31));
              callback.accept(job);
              return new BacktestCreateResponse(job.getJobId(), job.getStatus());
            })
        .when(requests)
        .request(eq(1L), any(), any());
    var response =
        service.request(1L, new NaturalLanguageBacktestRequest("삼성전자 2024년 매수 후 보유", null));
    assertThat(response.outcome()).isEqualTo("ACCEPTED");
    verify(usage).reserve(1L, UsageType.BACKTEST);
    verify(usage, never()).reserve(1L, UsageType.AI_AGENT);
    verify(usage, never()).release(any(UsageLimitService.UsageReservation.class));
    verify(jobs)
        .attachNaturalLanguageMetadata(
            any(), anyString(), eq("model"), eq("backtest-request-parser-v1"), eq(10L), eq(5L));
  }

  private void setId(Asset asset, Long id) {
    try {
      var field = Asset.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(asset, id);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
