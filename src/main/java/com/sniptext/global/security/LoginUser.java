package com.sniptext.global.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.swagger.v3.oas.annotations.Parameter;

/**
 * 컨트롤러 파라미터에 로그인 회원 번호를 넣는다. 예: {@code @LoginUser Long userId}
 * 인증 정보가 없으면 COMMON401_1. 스웨거에는 요청 파라미터로 나오지 않는다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(hidden = true)
public @interface LoginUser {
}
