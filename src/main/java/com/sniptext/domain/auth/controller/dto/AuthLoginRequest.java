package com.sniptext.domain.auth.controller.dto;

import com.sniptext.domain.auth.service.dto.command.AuthLoginCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 구글 로그인 (명세 1번). 빈 값이 구글까지 가면 구글 오류(AUTH502_1)로 둔갑하므로 여기서 COMMON400_1로 막는다.
 * provider를 열거형으로 받으면 잘못된 값이 AUTH400_1이 아니라 파싱 실패(COMMON400_1)가 되어 문자열로 받는다.
 */
public record AuthLoginRequest(
	@NotBlank String code,
	@NotBlank String codeVerifier,
	@NotBlank String redirectUri,
	@NotNull(message = "AUTH400_1") @Pattern(regexp = "GOOGLE", message = "AUTH400_1") String provider
) {

	public AuthLoginCommand toCommand() {
		return new AuthLoginCommand(code, codeVerifier, redirectUri);
	}

}
