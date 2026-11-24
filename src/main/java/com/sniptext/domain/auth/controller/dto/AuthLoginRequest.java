package com.sniptext.domain.auth.controller.dto;

import com.sniptext.domain.auth.service.dto.command.AuthLoginCommand;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 구글 로그인 (명세 1번). 빈 값이 구글까지 가면 구글 오류(AUTH502_1)로 둔갑하므로 여기서 COMMON400_1로 막는다.
 * provider를 열거형으로 받으면 잘못된 값이 AUTH400_1이 아니라 파싱 실패(COMMON400_1)가 되어 문자열로 받는다.
 */
public record AuthLoginRequest(
	@Schema(description = "구글 로그인 뒤 리디렉션 주소로 받은 인가 코드 (한 번만 쓸 수 있음)",
		requiredMode = Schema.RequiredMode.REQUIRED, example = "4/0AQSTgQ...")
	@NotBlank String code,
	@Schema(description = "인가 요청 때 만든 PKCE 코드 검증값 (code_challenge_method=S256)",
		requiredMode = Schema.RequiredMode.REQUIRED, example = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
	@NotBlank String codeVerifier,
	@Schema(description = "인가 요청에 쓴 리디렉션 주소. 서버 허용 목록과 끝 슬래시까지 정확히 같아야 함",
		requiredMode = Schema.RequiredMode.REQUIRED, example = "https://sniptext.kr/auth/google/callback")
	@NotBlank String redirectUri,
	@Schema(description = "로그인 방식. 지금은 GOOGLE만", allowableValues = {"GOOGLE"},
		requiredMode = Schema.RequiredMode.REQUIRED, example = "GOOGLE")
	@NotNull(message = "AUTH400_1") @Pattern(regexp = "GOOGLE", message = "AUTH400_1") String provider
) {

	public AuthLoginCommand toCommand() {
		return new AuthLoginCommand(code, codeVerifier, redirectUri);
	}

}
