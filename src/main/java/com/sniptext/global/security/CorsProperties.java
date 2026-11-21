package com.sniptext.global.security;

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
 *                       브라우저가 보내는 Origin과 정확히 같아야 해서 경로·끝 슬래시 없이 {@code 스킴://호스트[:포트]}만 받는다
 */
@Validated
@ConfigurationProperties("sniptext.cors")
public record CorsProperties(
	@NotEmpty
	List<@Pattern(regexp = "https?://[A-Za-z0-9.-]+(:[0-9]+)?") String> allowedOrigins
) {
}
