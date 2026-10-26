package com.sniptext.global.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.sniptext.global.exception.CommonErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Authorization: Bearer 접근 토큰이 유효하면 회원 번호를 인증 정보로 넣는다.
 * 실패해도 바로 응답하지 않고 요청 속성에 응답할 결과 코드만 남긴다. 공개 경로는 그대로 통과하고,
 * 로그인이 필요한 경로에서는 ApiAuthenticationEntryPoint가 이 코드로 응답한다.
 * <p>
 * 서블릿 필터로 한 번 더 자동 등록되지 않도록 빈으로 만들지 않고 SecurityConfig에서 직접 만든다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	static final String AUTH_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".ERROR";

	private static final String BEARER = "Bearer";

	private final JwtTokenProvider tokenProvider;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		// Bearer 접두어가 없으면 토큰 없음으로 본다 (COMMON401_1)
		if (isBearer(header)) {
			authenticate(request, header.substring(BEARER.length()).trim());
		}
		chain.doFilter(request, response);
	}

	private void authenticate(HttpServletRequest request, String token) {
		if (token.isEmpty()) {
			request.setAttribute(AUTH_ERROR_ATTRIBUTE, CommonErrorCode.INVALID_TOKEN);
			return;
		}
		switch (tokenProvider.parseAccessToken(token)) {
			case AccessTokenResult.Authenticated(long userId) -> setAuthentication(userId);
			case AccessTokenResult.Expired expired -> request.setAttribute(AUTH_ERROR_ATTRIBUTE,
				CommonErrorCode.UNAUTHORIZED);
			case AccessTokenResult.Invalid invalid -> request.setAttribute(AUTH_ERROR_ATTRIBUTE,
				CommonErrorCode.INVALID_TOKEN);
		}
	}

	private static void setAuthentication(long userId) {
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(
			UsernamePasswordAuthenticationToken.authenticated(userId, null, AuthorityUtils.NO_AUTHORITIES));
		SecurityContextHolder.setContext(context);
	}

	// 스킴 이름은 대소문자를 구분하지 않는다. "Bearer"만 있거나 뒤가 공백이면 깨진 토큰으로 처리된다
	private static boolean isBearer(String header) {
		if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
			return false;
		}
		return header.length() == BEARER.length() || Character.isWhitespace(header.charAt(BEARER.length()));
	}

}
