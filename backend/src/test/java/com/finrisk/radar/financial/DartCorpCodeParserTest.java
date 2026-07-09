package com.finrisk.radar.financial;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DartCorpCodeParserTest {
	private final DartCorpCodeParser parser = new DartCorpCodeParser();

	@Test void parsesSamsungCorpCodeFromXml() {
		String xml = """
				<result>
					<list>
						<corp_code>00126380</corp_code>
						<corp_name>삼성전자</corp_name>
						<stock_code>005930</stock_code>
						<modify_date>20240101</modify_date>
					</list>
				</result>
				""";

		var entries = parser.parse(xml);

		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).corpCode()).isEqualTo("00126380");
		assertThat(entries.get(0).stockCode()).isEqualTo("005930");
	}
}
