package com.sniptext.domain.auth.controller;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Pattern;

/**
 * 재발급 토큰 쿠키 설정. 화면과 API가 같은 사이트면 기본값 Lax로 쿠키가 오간다.
 * 서로 다른 사이트에서 호출하는 환경(예: 로컬 화면 → 개발 서버)은 None이어야 쿠키가 오간다.
 */
@Validated
@ConfigurationProperties("sniptext.auth.cookie")
public record AuthCookieProperties(
	@DefaultValue("Lax") @Pattern(regexp = "Strict|Lax|None") String sameSite
) {
}
