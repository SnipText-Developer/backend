package com.sniptext.global.client.google;

import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 구글 인가 코드를 토큰 엔드포인트에서 교환하고, 받은 ID 토큰을 {@link GoogleIdTokenVerifier}로 검증한다.
 * 재시도하지 않는다. 인가 코드는 한 번만 쓸 수 있어서, 시간 초과로 끝났어도 구글 쪽에서는 이미 교환됐을 수 있다.
 * <p>
 * 결과는 세 가지다. 검증된 계정 정보, 빈 값(잘못된·만료·재사용 코드, 허용 목록에 없는 redirectUri, ID 토큰 검증 실패),
 * {@link GoogleLoginException}(구글 장애·연결 실패·시간 초과, 우리 설정 오류, 검증에 쓸 구글 공개키 조회 실패).
 * 요청 팩토리(연결·응답 시간 제한)는 넘겨받은 빌더에 이미 들어 있어야 한다 (테스트에서 가짜 서버를 묶을 수 있게).
 */
public class GoogleAuthCodeClient {

	private static final String AUTHORIZATION_CODE = "authorization_code";
	// 코드가 잘못됐거나 만료·재사용된 경우 (PKCE 검증 실패도 여기로 온다). 나머지 오류는 우리 설정 문제다
	private static final String INVALID_GRANT = "invalid_grant";

	private final RestClient restClient;
	private final GoogleProperties properties;
	private final GoogleIdTokenVerifier idTokenVerifier;

	public GoogleAuthCodeClient(RestClient.Builder restClientBuilder, GoogleProperties properties,
		GoogleIdTokenVerifier idTokenVerifier) {
		this.restClient = restClientBuilder.build();
		this.properties = properties;
		this.idTokenVerifier = idTokenVerifier;
	}

	public Optional<GoogleAccount> exchange(String code, String codeVerifier, String redirectUri) {
		// 요청 값을 그대로 구글에 넘기지 않는다. 허용 목록에 없으면 구글에 묻지 않고 거절한다
		if (!properties.redirectUris().contains(redirectUri)) {
			return Optional.empty();
		}
		return requestIdToken(code, codeVerifier, redirectUri).flatMap(idTokenVerifier::verify);
	}

	/** 교환에 성공하면 ID 토큰, invalid_grant면 빈 값이다. */
	private Optional<String> requestIdToken(String code, String codeVerifier, String redirectUri) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", AUTHORIZATION_CODE);
		form.add("code", code);
		form.add("code_verifier", codeVerifier);
		form.add("redirect_uri", redirectUri);
		form.add("client_id", properties.clientId());
		form.add("client_secret", properties.clientSecret());
		try {
			return restClient.post()
				.uri(properties.tokenUri())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.accept(MediaType.APPLICATION_JSON)
				.body(form)
				.exchange((request, response) -> {
					int status = response.getStatusCode().value();
					if (response.getStatusCode().is2xxSuccessful()) {
						TokenResponse token = response.bodyTo(TokenResponse.class);
						if (token == null || token.idToken() == null || token.idToken().isBlank()) {
							throw new GoogleLoginException("구글 토큰 응답에 ID 토큰 없음");
						}
						return Optional.of(token.idToken());
					}
					String error = readError(response);
					if (status == 400 && INVALID_GRANT.equals(error)) {
						return Optional.empty();
					}
					throw new GoogleLoginException("구글 토큰 교환 실패: " + status + " " + error);
				});
		} catch (ResourceAccessException e) {
			// 연결 실패·시간 초과. 원인 예외는 붙이지 않는다
			throw new GoogleLoginException("구글 토큰 교환 통신 실패: " + e.getClass().getSimpleName());
		} catch (RestClientException e) {
			throw new GoogleLoginException("구글 토큰 응답 처리 실패: " + e.getClass().getSimpleName());
		}
	}

	// 오류 응답의 error 값만 읽는다 (error_description 등 나머지는 로그에 남기지 않는다)
	private static String readError(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
		try {
			ErrorResponse error = response.bodyTo(ErrorResponse.class);
			return error == null || error.error() == null ? "unknown" : error.error();
		} catch (RuntimeException e) {
			return "unreadable";
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TokenResponse(@JsonProperty("id_token") String idToken) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ErrorResponse(String error) {
	}

}
