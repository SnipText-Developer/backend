package com.sniptext.domain.template.annotation;

import com.sniptext.domain.template.entity.UsageChannel;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** UsageChannel의 상수 이름과 정확히 같은지만 본다. 위반 메시지는 어노테이션 기본값(TEMPLATE400_9) 그대로다. */
public class UsageChannelConstraintValidator implements ConstraintValidator<ValidUsageChannel, String> {

	@Override
	public boolean isValid(String channel, ConstraintValidatorContext context) {
		return UsageChannel.isSupported(channel);
	}

}
