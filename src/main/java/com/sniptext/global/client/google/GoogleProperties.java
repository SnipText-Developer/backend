package com.sniptext.global.client.google;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * 구글 로그인 설정 (sniptext.google). 클라이언트 ID는 ID 토큰의 대상 검사에 쓴다.
 * 비어 있으면 기동에 실패한다. 인가 코드 교환에 필요한 값도 로그인 API에서 이 레코드에 더한다.
 */
@Validated
@ConfigurationProperties("sniptext.google")
public record GoogleProperties(
	@NotBlank
	String clientId
) {
}
