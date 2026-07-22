package com.finrisk.radar.rag;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.finrisk.radar.global.error.GlobalExceptionHandler;
import com.finrisk.radar.rag.api.*;
import com.finrisk.radar.rag.service.RagSearchService;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RagControllerTest {
  private RagSearchService search;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    search = mock(RagSearchService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new RagController(search))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void searchesWithPostBody() throws Exception {
    when(search.search(any())).thenReturn(List.of());

    mvc.perform(
            post("/api/rag/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"단기 상환 위험\",\"limit\":5}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray());
  }

  @Test
  void rejectsBlankQueryAndLimitAboveTwenty() throws Exception {
    mvc.perform(
            post("/api/rag/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\" \",\"limit\":21}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_001"));
    verifyNoInteractions(search);
  }
}
