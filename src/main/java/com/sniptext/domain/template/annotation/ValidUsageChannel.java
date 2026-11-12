package com.sniptext.domain.template.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 요청 객체의 사용 채널 검증. 없거나(null 포함) 지원하지 않으면 TEMPLATE400_9.
 * 필드를 열거형으로 받으면 틀린 값이 JSON 역직렬화 실패(COMMON400_1)가 되므로 문자열로 받아 여기서 검사한다.
 * UsageChannel(entity)을 쓰므로 controller 패키지 밖에 둔다 (ArchUnit 의존 규칙).
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UsageChannelConstraintValidator.class)
public @interface ValidUsageChannel {

	String message() default "TEMPLATE400_9";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
