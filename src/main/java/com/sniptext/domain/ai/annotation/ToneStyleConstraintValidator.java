package com.sniptext.domain.ai.annotation;

import java.util.Arrays;

import com.sniptext.domain.ai.entity.ToneStyle;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** ToneStyle 상수 이름 중 하나와 대소문자까지 같을 때만 통과한다. */
public class ToneStyleConstraintValidator implements ConstraintValidator<ValidToneStyle, String> {

	@Override
	public boolean isValid(String style, ConstraintValidatorContext context) {
		return style != null && Arrays.stream(ToneStyle.values()).anyMatch(each -> each.name().equals(style));
	}

}
