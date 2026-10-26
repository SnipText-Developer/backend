package com.sniptext.global.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.sniptext.global.exception.BusinessException;
import com.sniptext.global.exception.CommonErrorCode;

/** {@link LoginUser} Long 파라미터에 토큰 필터가 넣은 회원 번호를 넣는다. 익명이면 COMMON401_1. */
@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(LoginUser.class) && Long.class.equals(parameter.getParameterType());
	}

	@Override
	public Long resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
		NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
			throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
		}
		return userId;
	}

}
