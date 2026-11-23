package com.sniptext.global.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

/**
 * 출처 허용 설정 (sniptext.cors). 허용 출처는 기본값이 없어 빠뜨리면 기동에 실패한다. 프로필별 값은 설계 문서 4-10.
 *
 * @param allowedOrigins 브라우저에서 API를 부를 수 있는 화면 주소 (환경 변수는 쉼표로 구분).
 *                       재발급 쿠키 때문에 자격 증명을 허용하므로 {@code *}는 쓸 수 없고,
 *                       브라우저가 보내는 Origin과 정확히 같아야 해서 경로·끝 슬래시 없이 {@code 스킴://호스트[:포트]}만 받는다.
 *                       크롬 확장은 {@code chrome-extension://확장 ID}(a~p 소문자 32자)로 받는다 (S2 명세 공통 규칙)
 */
@Validated
@ConfigurationProperties("sniptext.cors")
public record CorsProperties(
	@NotEmpty
	List<@Pattern(regexp = ORIGIN_PATTERN) String> allowedOrigins
) {

	/** 웹 화면 출처 또는 크롬 확장 출처. 테라폼 변수 cors_allowed_origins도 같은 규칙으로 검사한다 */
	static final String ORIGIN_PATTERN = "https?://[A-Za-z0-9.-]+(:[0-9]+)?|chrome-extension://[a-p]{32}";

}
