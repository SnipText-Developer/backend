package com.sniptext.global.client.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

class AnthropicLanguageModelClientTest {

	private static final String BASE_URL = "https://llm.test";
	private static final String API_KEY = "secret-api-key-1234";
	private static final String MODEL = "test-model";
	private static final String ERROR_BODY = "{\"type\":\"error\",\"error\":{\"message\":\"비밀-응답-본문\"}}";
	private static final LanguageModelRequest REQUEST = new LanguageModelRequest("시스템 지시", "<text>\n원문\n</text>", 4000);

	private MockRestServiceServer server;
	private AnthropicLanguageModelClient client;
	private HttpServer slowServer;
	private final CountDownLatch releaseSlowServer = new CountDownLatch(1);

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new AnthropicLanguageModelClient(builder, properties(BASE_URL, Duration.ofSeconds(20)));
	}

	@AfterEach
	void tearDown() {
		releaseSlowServer.countDown();
		if (slowServer != null) {
			slowServer.stop(0);
		}
	}

	@Test
	@DisplayName("POST /v1/messages에 키·버전·JSON 헤더와 model·max_tokens·system·messages 본문을 보낸다")
	void request() {
		server.expect(requestTo(BASE_URL + "/v1/messages"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("x-api-key", API_KEY))
			.andExpect(header("anthropic-version", "2023-06-01"))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.model").value(MODEL))
			.andExpect(jsonPath("$.max_tokens").value(4000))
			.andExpect(jsonPath("$.system").value("시스템 지시"))
			.andExpect(jsonPath("$.messages.length()").value(1))
			.andExpect(jsonPath("$.messages[0].role").value("user"))
			.andExpect(jsonPath("$.messages[0].content").value("<text>\n원문\n</text>"))
			.andRespond(withSuccess(response("end_turn", "{\"type\":\"text\",\"text\":\"교정\"}"),
				MediaType.APPLICATION_JSON));

		client.complete(REQUEST);

		server.verify();
	}

	@Test
	@DisplayName("end_turn이면 text 블록들을 이어 붙여 돌려주고 다른 종류의 블록은 건너뛴다")
	void endTurnJoinsTextBlocks() {
		respond(response("end_turn",
			"{\"type\":\"thinking\",\"thinking\":\"\",\"signature\":\"x\"}",
			"{\"type\":\"text\",\"text\":\"안녕하세요.\\n\"}",
			"{\"type\":\"text\",\"text\":\"감사합니다.\"}"));

		LanguageModelResult result = client.complete(REQUEST);

		assertThat(result.text()).isEqualTo("안녕하세요.\n감사합니다.");
		assertThat(result.refused()).isFalse();
	}

	@Test
	@DisplayName("stop_sequence도 정상 응답이다")
	void stopSequence() {
		respond(response("stop_sequence", "{\"type\":\"text\",\"text\":\"교정\"}"));

		assertThat(client.complete(REQUEST).text()).isEqualTo("교정");
	}

	@Test
	@DisplayName("stop_reason이 refusal이면 refused=true다")
	void refusal() {
		respond(response("refusal"));

		assertThat(client.complete(REQUEST).refused()).isTrue();
	}

	@Test
	@DisplayName("stop_reason이 max_tokens면 잘린 결과를 내보내지 않고 LanguageModelException이다")
	void maxTokens() {
		respond(response("max_tokens", "{\"type\":\"text\",\"text\":\"잘린 결\"}"));

		assertThatThrownBy(() -> client.complete(REQUEST)).isInstanceOf(LanguageModelException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{\"content\":[],\"stop_reason\":\"end_turn\"}",
		"{\"stop_reason\":\"end_turn\"}",
		"{\"content\":[{\"type\":\"text\",\"text\":\"\"}],\"stop_reason\":\"end_turn\"}",
		"{\"content\":[{\"type\":\"text\",\"text\":\" \\n\\u00a0\"}],\"stop_reason\":\"end_turn\"}",
		"{\"content\":[{\"type\":\"text\",\"text\":\"교정\"}],\"stop_reason\":\"tool_use\"}",
		"{\"content\":[{\"type\":\"text\",\"text\":\"교정\"}],\"stop_reason\":\"pause_turn\"}",
		"{\"content\":[{\"type\":\"text\",\"text\":\"교정\"}]}"
	})
	@DisplayName("text 블록 없음, 빈 텍스트, 모르는 stop_reason이면 LanguageModelException이다")
	void unusableResponse(String body) {
		respond(body);

		assertThatThrownBy(() -> client.complete(REQUEST)).isInstanceOf(LanguageModelException.class);
	}

	@ParameterizedTest
	@ValueSource(ints = {400, 401, 429, 500, 529})
	@DisplayName("HTTP 오류는 상태 코드만 담은 LanguageModelException이고 응답 본문·키가 없으며 원인도 없다")
	void httpError(int status) {
		server.expect(requestTo(BASE_URL + "/v1/messages"))
			.andRespond(withStatus(HttpStatusCode.valueOf(status))
				.contentType(MediaType.APPLICATION_JSON)
				.body(ERROR_BODY));

		assertThatThrownBy(() -> client.complete(REQUEST))
			.isInstanceOf(LanguageModelException.class)
			.hasMessageContaining(String.valueOf(status))
			.hasNoCause()
			.satisfies(e -> assertThat(e.getMessage()).doesNotContain("비밀-응답-본문", API_KEY, "원문"));
	}

	@Test
	@DisplayName("응답이 응답 제한 시간보다 늦으면 LanguageModelTimeoutException이고 원인이 없다")
	void timeout() throws IOException {
		slowServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		slowServer.createContext("/v1/messages", exchange -> {
			try {
				releaseSlowServer.await(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		slowServer.start();
		AnthropicLanguageModelClient realClient = realClient(
			"http://127.0.0.1:" + slowServer.getAddress().getPort(), Duration.ofMillis(200));

		assertThatThrownBy(() -> realClient.complete(REQUEST))
			.isInstanceOf(LanguageModelTimeoutException.class)
			.hasNoCause();
	}

	@Test
	@DisplayName("연결할 수 없으면 LanguageModelException이고 원인이 없다")
	void connectionRefused() throws IOException {
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			closedPort = socket.getLocalPort();
		}
		AnthropicLanguageModelClient realClient = realClient("http://127.0.0.1:" + closedPort, Duration.ofSeconds(2));

		assertThatThrownBy(() -> realClient.complete(REQUEST))
			.isInstanceOf(LanguageModelException.class)
			.hasNoCause();
	}

	private void respond(String body) {
		server.expect(requestTo(BASE_URL + "/v1/messages"))
			.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
	}

	private static String response(String stopReason, String... blocks) {
		return "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[%s],\"stop_reason\":\"%s\",\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}"
			.formatted(String.join(",", blocks), stopReason);
	}

	private static AnthropicLanguageModelClient realClient(String baseUrl, Duration timeout) {
		return new AnthropicLanguageModelClient(
			RestClient.builder().requestFactory(LanguageModelConfig.requestFactory(timeout)),
			properties(baseUrl, timeout));
	}

	private static LanguageModelProperties properties(String baseUrl, Duration timeout) {
		return new LanguageModelProperties(LanguageModelProperties.Provider.ANTHROPIC, API_KEY, MODEL, baseUrl, timeout);
	}

}
