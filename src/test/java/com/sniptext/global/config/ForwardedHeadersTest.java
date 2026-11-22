package com.sniptext.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import com.sniptext.TestcontainersConfiguration;

/**
 * 운영에서는 Caddy가 https를 받고 앱에는 http로 넘긴다 (docs/deploy-design.md 4-9).
 * 프록시가 붙인 X-Forwarded-* 헤더로 원래 주소를 판단하는지 실제 톰캣으로 확인한다 (MockMvc는 톰캣을 거치지 않는다).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ForwardedHeadersTest {

	@LocalServerPort
	private int port;

	@Test
	@DisplayName("프록시 뒤에서 들어온 요청은 X-Forwarded-Proto·Host 기준의 https 주소로 판단한다")
	void forwardedHttpsRequest() {
		// 스웨거 문서의 서버 주소는 요청 주소로 만든다. http로 판단하면 https 화면에서 "Try it out"이 막힌다
		String body = RestClient.create("http://localhost:" + port)
			.get()
			.uri("/v3/api-docs")
			.header("X-Forwarded-Proto", "https")
			.header("X-Forwarded-Host", "api.sniptext.kr")
			.header("X-Forwarded-For", "203.0.113.10")
			.retrieve()
			.body(String.class);

		assertThat(body).contains("\"url\":\"https://api.sniptext.kr\"");
	}

}
