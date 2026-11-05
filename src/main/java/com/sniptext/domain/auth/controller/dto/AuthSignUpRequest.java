package com.sniptext.domain.auth.controller.dto;

import com.sniptext.domain.auth.service.dto.command.AuthSignUpCommand;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 약관 동의 후 가입 완료 (명세 2번). 필수 약관 동의는 true만 허용한다. */
public record AuthSignUpRequest(
	@NotBlank String signupToken,
	@NotNull(message = "AUTH400_3") @AssertTrue(message = "AUTH400_3") Boolean termsAgreed
) {

	public AuthSignUpCommand toCommand() {
		return new AuthSignUpCommand(signupToken);
	}

}
