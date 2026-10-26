package com.sniptext.domain.template.annotation;

import java.util.Optional;

import com.sniptext.domain.template.entity.Shortcut;
import com.sniptext.domain.template.entity.ShortcutViolation;
import com.sniptext.domain.template.exception.TemplateErrorCode;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Shortcut의 규칙으로 판단하고, 첫 번째 위반의 결과 코드 하나만 메시지로 남긴다. */
public class ShortcutConstraintValidator implements ConstraintValidator<ValidShortcut, String> {

	@Override
	public boolean isValid(String shortcut, ConstraintValidatorContext context) {
		Optional<ShortcutViolation> violation = Shortcut.findViolation(shortcut);
		if (violation.isEmpty()) {
			return true;
		}

		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(TemplateErrorCode.from(violation.get()).getCode())
			.addConstraintViolation();
		return false;
	}

}
