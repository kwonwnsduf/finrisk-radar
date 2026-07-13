package com.finrisk.radar;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finrisk.radar.asset.AssetRepository;
import com.finrisk.radar.backtest.BacktestJobRepository;
import com.finrisk.radar.backtest.BacktestResultRepository;
import com.finrisk.radar.collector.log.CollectionLogRepository;
import com.finrisk.radar.financial.DartCorpCodeRepository;
import com.finrisk.radar.financial.DebtMaturityRepository;
import com.finrisk.radar.financial.FinancialCollectionLogRepository;
import com.finrisk.radar.financial.FinancialMetricRepository;
import com.finrisk.radar.marketprice.MarketPriceRepository;
import com.finrisk.radar.marketprice.MarketPriceWriter;
import com.finrisk.radar.risk.AssetRelationshipRepository;
import com.finrisk.radar.risk.CreditEventRepository;
import com.finrisk.radar.risk.RiskCalculationJobRepository;
import com.finrisk.radar.risk.RiskScoreRepository;
import com.finrisk.radar.risk.RiskSignalRepository;
import com.finrisk.radar.user.UserRepository;
import com.finrisk.radar.watchlist.WatchlistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
      "management.health.diskspace.enabled=false",
      "spring.kafka.listener.auto-startup=false",
      "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
      "jwt.access-token-expiration=30m",
      "jwt.refresh-token-expiration=14d",
      "spring.security.oauth2.client.registration.google.client-id=test-client",
      "spring.security.oauth2.client.registration.google.client-secret=test-secret"
    })
@AutoConfigureObservability
@AutoConfigureMockMvc
class BackendApplicationTests {

  @MockitoBean private UserRepository userRepository;

  @MockitoBean private WatchlistRepository watchlistRepository;

  @MockitoBean private AssetRepository assetRepository;

  @MockitoBean private MarketPriceRepository marketPriceRepository;

  @MockitoBean private MarketPriceWriter marketPriceWriter;

  @MockitoBean private CollectionLogRepository collectionLogRepository;

  @MockitoBean private BacktestJobRepository backtestJobRepository;

  @MockitoBean private BacktestResultRepository backtestResultRepository;

  @MockitoBean private DartCorpCodeRepository dartCorpCodeRepository;

  @MockitoBean private FinancialMetricRepository financialMetricRepository;

  @MockitoBean private DebtMaturityRepository debtMaturityRepository;

  @MockitoBean private FinancialCollectionLogRepository financialCollectionLogRepository;

  @MockitoBean private RiskCalculationJobRepository riskCalculationJobRepository;
  @MockitoBean private RiskScoreRepository riskScoreRepository;
  @MockitoBean private RiskSignalRepository riskSignalRepository;
  @MockitoBean private CreditEventRepository creditEventRepository;
  @MockitoBean private AssetRelationshipRepository assetRelationshipRepository;

  @Autowired private MockMvc mockMvc;

  @Test
  void contextLoads() {}

  @Test
  void apiHealthReturnsSuccessResponse() throws Exception {
    mockMvc
        .perform(get("/api/health").header("Authorization", "Bearer invalid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data").value("UP"));
  }

  @Test
  void currentUserRequiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/api/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("AUTH_003"));
  }

  @Test
  void actuatorHealthIsExposed() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void prometheusMetricsAreExposed() throws Exception {
    mockMvc
        .perform(get("/actuator/prometheus"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("jvm_info")));
  }

  @Test
  void swaggerUiIsExposed() throws Exception {
    mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
  }

  @Test
  void openApiDocumentIsExposed() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("FinRisk Radar API"))
        .andExpect(jsonPath("$.paths['/api/backtests']").exists())
        .andExpect(jsonPath("$.paths['/api/backtests/{jobId}']").exists());
  }
}
