package com.sniptext.global.client.google;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 구글 로그인 설정 (sniptext.google). 클라이언트 ID·시크릿·redirectUri 허용 목록은 기본값이 없어 빠뜨리면 기동에 실패한다.
 * 시크릿이 로그에 나오지 않게 toString을 재정의한다 (설정 바인딩 실패 로그 등).
 *
 * @param clientId       ID 토큰의 대상 검사와 코드 교환에 쓴다
 * @param clientSecret   코드 교환에 쓴다 (웹 클라이언트는 PKCE를 써도 필요하다)
 * @param redirectUris   로그인 요청의 redirectUri가 이 목록에 있어야 교환한다 (환경 변수는 쉼표로 구분)
 * @param tokenUri       구글 토큰 엔드포인트
 * @param jwkSetUri      ID 토큰 서명을 확인할 구글 공개키 주소
 * @param connectTimeout 연결 제한 (코드 교환과 공개키 조회에 같이 쓴다)
 * @param readTimeout    응답 제한. 로그인 화면이 오래 멈추지 않게 짧게 둔다
 */
@Validated
@ConfigurationProperties("sniptext.google")
public record GoogleProperties(
	@NotBlank
	String clientId,
	@NotBlank
	String clientSecret,
	@NotEmpty
	List<@NotBlank String> redirectUris,
	@NotBlank
	@DefaultValue("https://oauth2.googleapis.com/token")
	String tokenUri,
	@NotBlank
	@DefaultValue("https://www.googleapis.com/oauth2/v3/certs")
	String jwkSetUri,
	@NotNull
	@DefaultValue("3s")
	Duration connectTimeout,
	@NotNull
	@DefaultValue("5s")
	Duration readTimeout
) {

	@Override
	public String toString() {
		return ("GoogleProperties[clientId=%s, redirectUris=%s, tokenUri=%s, jwkSetUri=%s, connectTimeout=%s, "
			+ "readTimeout=%s]").formatted(clientId, redirectUris, tokenUri, jwkSetUri, connectTimeout, readTimeout);
	}

}
