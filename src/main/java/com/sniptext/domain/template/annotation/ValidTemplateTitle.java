package com.sniptext.domain.template.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 요청 객체의 템플릿 이름 검증. 위반 이유에 맞는 결과 코드(TEMPLATE400_1·2)를 메시지로 남긴다.
 * 필수 값이라 null도 빈 값(TEMPLATE400_1)으로 본다. @NotBlank를 같이 붙이지 않는다 (공백 기준이 화면과 달라진다).
 * TemplateTitle(entity)을 쓰므로 controller 패키지 밖에 둔다 (ArchUnit 의존 규칙).
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TemplateTitleConstraintValidator.class)
public @interface ValidTemplateTitle {

	String message() default "TEMPLATE400_1";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
