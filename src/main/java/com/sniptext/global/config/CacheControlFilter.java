package com.sniptext.global.config;

import java.io.IOException;

import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 캐시 헤더(명세 공통 규칙 7장). GET /api/v1/gallery/**만 공개 캐시, 나머지는 전부 캐시 금지.
 * 시큐리티 필터보다 먼저 돌아 401·403 응답에도 붙는다. 에러 응답은 경로와 상관없이 전역 예외 처리가 no-store로 덮어쓴다.
 */
@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1)
public class CacheControlFilter extends OncePerRequestFilter {

	static final String PUBLIC_CACHE = "public, max-age=300";
	static final String NO_STORE = "no-store";

	private static final PathPattern PUBLIC_PATH = PathPatternParser.defaultInstance.parse("/api/v1/gallery/**");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		response.setHeader(HttpHeaders.CACHE_CONTROL, isPublic(request) ? PUBLIC_CACHE : NO_STORE);
		chain.doFilter(request, response);
	}

	private static boolean isPublic(HttpServletRequest request) {
		return HttpMethod.GET.matches(request.getMethod())
			&& PUBLIC_PATH.matches(PathContainer.parsePath(request.getRequestURI()));
	}

}
