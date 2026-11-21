package com.sniptext.global.security;

import java.time.Duration;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 출처 허용(CORS). 시큐리티 필터 체인 맨 앞에서 처리해, 로그인이 필요한 경로의 사전 요청도 토큰 없이 통과하고
 * 401 같은 에러 응답에도 허용 헤더가 붙는다 (화면이 에러 코드를 읽어야 재발급할 수 있다).
 * 허용하지 않은 출처의 요청은 403으로 거절된다.
 */
@Configuration
public class CorsConfig {

	// 명세(s1-api-spec)에 쓰는 메서드
	private static final List<String> ALLOWED_METHODS = List.of(
		HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
		HttpMethod.PATCH.name(), HttpMethod.DELETE.name());

	// 사전 요청 결과를 브라우저가 기억하는 시간 (크롬은 최대 2시간까지만 쓴다)
	private static final Duration PREFLIGHT_MAX_AGE = Duration.ofHours(1);

	@Bean
	public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(properties.allowedOrigins());
		configuration.setAllowedMethods(ALLOWED_METHODS);
		// 요청 헤더는 화면이 보내는 그대로 허용한다 (Authorization, Content-Type 등). 출처를 좁혔으므로 헤더까지 막을 이유가 없다
		configuration.setAllowedHeaders(List.of(CorsConfiguration.ALL));
		// 재발급 토큰 쿠키를 주고받는다
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(PREFLIGHT_MAX_AGE);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

}
