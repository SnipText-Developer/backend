package com.sniptext.domain.ai.annotation;

import com.sniptext.domain.ai.exception.AiErrorCode;
import com.sniptext.global.text.TextWhitespace;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** 비었으면 AI400_2, 길면 AI400_3을 메시지로 남긴다. */
public class ToneTextConstraintValidator implements ConstraintValidator<ValidToneText, String> {

	/** 교정할 원문 최대 길이 (명세 22번, 템플릿 본문 한도와 같다) */
	static final int MAX_LENGTH = 3000;

	@Override
	public boolean isValid(String text, ConstraintValidatorContext context) {
		if (text == null || TextWhitespace.isBlank(text)) {
			return reject(context, AiErrorCode.TEXT_EMPTY);
		}
		if (text.length() > MAX_LENGTH) {
			return reject(context, AiErrorCode.TEXT_TOO_LONG);
		}
		return true;
	}

	private static boolean reject(ConstraintValidatorContext context, AiErrorCode errorCode) {
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(errorCode.getCode()).addConstraintViolation();
		return false;
	}

}
