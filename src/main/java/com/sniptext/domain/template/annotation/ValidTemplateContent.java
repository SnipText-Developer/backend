package com.sniptext.domain.template.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 요청 객체의 템플릿 본문 검증. 위반 이유에 맞는 결과 코드(TEMPLATE400_6·7)를 메시지로 남긴다.
 * 필수 값이라 null도 빈 값(TEMPLATE400_6)으로 본다. @NotBlank·@Size를 같이 붙이지 않는다
 * (공백 기준이 화면과 달라지고, 줄바꿈 통일·NFC 정규화 전 길이로 세게 된다).
 * TemplateContent(entity)를 쓰므로 controller 패키지 밖에 둔다 (ArchUnit 의존 규칙).
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TemplateContentConstraintValidator.class)
public @interface ValidTemplateContent {

	String message() default "TEMPLATE400_6";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
