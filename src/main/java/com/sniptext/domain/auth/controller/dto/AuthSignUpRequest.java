package com.sniptext.domain.auth.controller.dto;

import com.sniptext.domain.auth.service.dto.command.AuthSignUpCommand;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 약관 동의 후 가입 완료 (명세 2번). 필수 약관 동의는 true만 허용한다. */
public record AuthSignUpRequest(
	@Schema(description = "1번 로그인에서 받은 가입용 토큰 (30분)", requiredMode = Schema.RequiredMode.REQUIRED,
		example = "eyJhbGciOiJIUzI1NiJ9.signup...")
	@NotBlank String signupToken,
	@Schema(description = "필수 약관(이용약관·개인정보 수집·이용) 동의. true만 허용", requiredMode = Schema.RequiredMode.REQUIRED,
		example = "true")
	@NotNull(message = "AUTH400_3") @AssertTrue(message = "AUTH400_3") Boolean termsAgreed
) {

	public AuthSignUpCommand toCommand() {
		return new AuthSignUpCommand(signupToken);
	}

}
