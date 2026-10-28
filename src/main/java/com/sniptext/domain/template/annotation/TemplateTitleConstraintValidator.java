package com.sniptext.domain.template.annotation;

import java.util.Optional;

import com.sniptext.domain.template.entity.TemplateTitle;
import com.sniptext.domain.template.entity.TemplateTitleViolation;
import com.sniptext.domain.template.exception.TemplateErrorCode;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** TemplateTitle의 규칙으로 판단하고, 첫 번째 위반의 결과 코드 하나만 메시지로 남긴다. */
public class TemplateTitleConstraintValidator implements ConstraintValidator<ValidTemplateTitle, String> {

	@Override
	public boolean isValid(String title, ConstraintValidatorContext context) {
		Optional<TemplateTitleViolation> violation = TemplateTitle.findViolation(title);
		if (violation.isEmpty()) {
			return true;
		}

		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(TemplateErrorCode.from(violation.get()).getCode())
			.addConstraintViolation();
		return false;
	}

}
