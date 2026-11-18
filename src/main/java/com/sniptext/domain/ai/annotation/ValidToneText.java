package com.sniptext.domain.ai.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 요청 객체의 교정할 원문 검증. null·빈 값·공백만(TextWhitespace 기준, NBSP·BOM 포함)이면 AI400_2,
 * String.length()가 3000을 넘으면 AI400_3. 정규화하지 않고 받은 그대로 센다 (NFC 규칙은 템플릿 이름·본문 대상).
 * @NotBlank·@Size를 같이 붙이지 않는다 (공백 기준이 화면과 달라진다).
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ToneTextConstraintValidator.class)
public @interface ValidToneText {

	String message() default "AI400_2";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
