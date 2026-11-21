package com.sniptext.global.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 출처 허용(CORS) 설정. 시큐리티 필터 체인(SecurityConfig)이 이 설정으로 맨 앞에서 처리해,
 * 로그인이 필요한 경로의 사전 요청도 토큰 없이 통과하고 401 같은 에러 응답에도 허용 헤더가 붙는다
 * (화면이 에러 코드를 읽어야 재발급할 수 있다). 허용하지 않은 출처는 COMMON403_1로 거절한다.
 * prod 프로필에서 https가 아니거나 로컬 주소인 출처가 있으면 기동에 실패한다 (출시 때 출시 전 값을 그대로 두지 않게).
 */
@Configuration
public class CorsConfig {

	// 명세(s1-api-spec)에 쓰는 메서드
	private static final List<String> ALLOWED_METHODS = List.of(
		HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
		HttpMethod.PATCH.name(), HttpMethod.DELETE.name());

	// 사전 요청 결과를 브라우저가 기억하는 시간 (크롬은 최대 2시간까지만 쓴다)
	private static final Duration PREFLIGHT_MAX_AGE = Duration.ofHours(1);

	private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1");

	@Bean
	public CorsConfigurationSource corsConfigurationSource(CorsProperties properties, Environment environment) {
		if (environment.matchesProfiles("prod")) {
			properties.allowedOrigins().forEach(CorsConfig::requireProductionOrigin);
		}

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

	private static void requireProductionOrigin(String origin) {
		URI uri = URI.create(origin);
		if (!"https".equals(uri.getScheme()) || LOCAL_HOSTS.contains(uri.getHost())) {
			throw new IllegalStateException(
				"운영 프로필의 허용 출처(CORS_ALLOWED_ORIGINS)는 로컬이 아닌 https 주소만 쓸 수 있습니다: " + origin);
		}
	}

}
