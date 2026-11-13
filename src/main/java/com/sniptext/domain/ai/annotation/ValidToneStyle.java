package com.sniptext.domain.ai.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 요청 객체의 문체 검증. null·빈 값·ToneStyle 상수 이름과 정확히 같지 않은 값(소문자 포함)이면 AI400_1.
 * 요청 필드를 ToneStyle로 받으면 없는 값이 JSON 변환 실패(COMMON400_1)가 되므로, 문자열로 받아 이것으로 검사한다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ToneStyleConstraintValidator.class)
public @interface ValidToneStyle {

	String message() default "AI400_1";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
