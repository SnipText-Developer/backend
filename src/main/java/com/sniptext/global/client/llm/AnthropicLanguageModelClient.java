package com.sniptext.global.client.llm;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sniptext.global.text.TextWhitespace;

/**
 * Anthropic Messages API(POST /v1/messages) 호출. 재시도하지 않는다 (응답 대기 20초 예산 안에서 끝내야 한다).
 * <p>
 * 실패는 전부 메시지에 상태 코드·오류 종류만 담은 예외로 바꾸고 원인을 붙이지 않는다.
 * RestClient 예외 메시지에는 응답 본문이 들어가므로, HTTP 오류는 onStatus에서 바로 바꿔 아예 만들어지지 않게 한다.
 * 요청 팩토리(연결·응답 시간 제한)는 넘겨받은 빌더에 이미 들어 있어야 한다 (테스트에서 가짜 서버를 묶을 수 있게).
 */
public class AnthropicLanguageModelClient implements LanguageModelClient {

	static final String API_VERSION = "2023-06-01";

	private static final String MESSAGES_PATH = "/v1/messages";
	private static final String TEXT_BLOCK = "text";
	private static final String END_TURN = "end_turn";
	private static final String STOP_SEQUENCE = "stop_sequence";
	private static final String REFUSAL = "refusal";
	private static final String MAX_TOKENS = "max_tokens";

	private final RestClient restClient;
	private final String model;

	public AnthropicLanguageModelClient(RestClient.Builder restClientBuilder, LanguageModelProperties properties) {
		this.restClient = restClientBuilder
			.baseUrl(properties.baseUrl())
			.defaultHeader("x-api-key", properties.apiKey())
			.defaultHeader("anthropic-version", API_VERSION)
			.build();
		this.model = properties.model();
	}

	@Override
	public LanguageModelResult complete(LanguageModelRequest request) {
		MessagesRequest body = new MessagesRequest(model, request.maxOutputTokens(), request.systemPrompt(),
			List.of(new Message("user", request.userText())));
		MessagesResponse response;
		try {
			response = restClient.post()
				.uri(MESSAGES_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.onStatus(HttpStatusCode::isError, (httpRequest, httpResponse) -> {
					throw new LanguageModelException("언어 모델 HTTP 오류: " + httpResponse.getStatusCode().value());
				})
				.body(MessagesResponse.class);
		} catch (ResourceAccessException e) {
			throw translate(e);
		} catch (RestClientException e) {
			throw new LanguageModelException("언어 모델 응답 처리 실패: " + e.getClass().getSimpleName());
		}
		return toResult(response);
	}

	private static RuntimeException translate(ResourceAccessException e) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			// 연결 시간 초과는 HttpTimeoutException의 하위라 먼저 거른다
			if (cause instanceof HttpConnectTimeoutException || cause instanceof ConnectException) {
				return new LanguageModelException("언어 모델 연결 실패");
			}
			if (cause instanceof HttpTimeoutException) {
				return new LanguageModelTimeoutException("언어 모델 응답 시간 초과");
			}
		}
		return new LanguageModelException("언어 모델 통신 실패: " + e.getClass().getSimpleName());
	}

	private static LanguageModelResult toResult(MessagesResponse response) {
		if (response == null) {
			throw new LanguageModelException("언어 모델 응답 없음");
		}
		String stopReason = response.stopReason();
		if (REFUSAL.equals(stopReason)) {
			return new LanguageModelResult(joinText(response), true);
		}
		if (MAX_TOKENS.equals(stopReason)) {
			// 잘린 결과는 내보내지 않는다
			throw new LanguageModelException("언어 모델 출력 토큰 상한 도달");
		}
		if (!END_TURN.equals(stopReason) && !STOP_SEQUENCE.equals(stopReason)) {
			throw new LanguageModelException("처리할 수 없는 stop_reason: " + stopReason);
		}
		if (textBlocks(response).isEmpty()) {
			throw new LanguageModelException("언어 모델 응답에 text 블록 없음");
		}
		String text = joinText(response);
		if (TextWhitespace.isBlank(text)) {
			throw new LanguageModelException("언어 모델 응답이 빈 텍스트");
		}
		return new LanguageModelResult(text, false);
	}

	private static List<ContentBlock> textBlocks(MessagesResponse response) {
		if (response.content() == null) {
			return List.of();
		}
		return response.content().stream()
			.filter(Objects::nonNull)
			.filter(block -> TEXT_BLOCK.equals(block.type()) && block.text() != null)
			.toList();
	}

	private static String joinText(MessagesResponse response) {
		return textBlocks(response).stream().map(ContentBlock::text).collect(Collectors.joining());
	}

	record MessagesRequest(String model, @JsonProperty("max_tokens") int maxTokens, String system,
		List<Message> messages) {
	}

	record Message(String role, String content) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record MessagesResponse(List<ContentBlock> content, @JsonProperty("stop_reason") String stopReason) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ContentBlock(String type, String text) {
	}

}
