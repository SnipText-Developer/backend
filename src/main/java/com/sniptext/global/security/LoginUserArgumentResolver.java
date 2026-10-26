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

/**
 * {@link LoginUser} Long 파라미터에 토큰 필터가 넣은 회원 번호를 넣는다. 익명이면 COMMON401_1.
 * <p>
 * 타입과 상관없이 {@code @LoginUser}가 붙은 파라미터는 모두 여기서 맡고, Long이 아니면 예외를 던진다.
 * 타입까지 보고 거절하면 잘못 쓴 파라미터(예: {@code @LoginUser long userId})가 기본 처리(@RequestParam)로 넘어가
 * {@code ?userId=999} 같은 쿼리 값이 로그인 회원 번호로 들어가기 때문이다.
 */
@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(LoginUser.class);
	}

	@Override
	public Long resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
		NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		if (!Long.class.equals(parameter.getParameterType())) {
			throw new IllegalStateException("@LoginUser는 Long 타입에만 쓸 수 있다");
		}
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
			throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
		}
		return userId;
	}

}
