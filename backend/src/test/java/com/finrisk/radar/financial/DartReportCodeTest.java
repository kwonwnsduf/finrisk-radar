package com.finrisk.radar.financial;

import com.finrisk.radar.global.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DartReportCodeTest {
	@Test void mapsQuarterToDartReportCode() {
		assertThat(DartReportCode.fromQuarter(1)).isEqualTo("11013");
		assertThat(DartReportCode.fromQuarter(2)).isEqualTo("11012");
		assertThat(DartReportCode.fromQuarter(3)).isEqualTo("11014");
		assertThat(DartReportCode.fromQuarter(4)).isEqualTo("11011");
	}

	@Test void rejectsInvalidQuarter() {
		assertThatThrownBy(() -> DartReportCode.fromQuarter(5)).isInstanceOf(BusinessException.class);
	}
}
