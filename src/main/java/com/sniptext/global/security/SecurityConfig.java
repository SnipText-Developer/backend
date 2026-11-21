package com.sniptext.global.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import lombok.RequiredArgsConstructor;

/**
 * 보안 설정. 접근 토큰 필터가 인증 정보를 넣고, 로그인이 필요한 경로에서 인증이 없으면 진입점이 401로 응답한다.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	private static final String[] SWAGGER_PATHS = {"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"};

	private final ApiAuthenticationEntryPoint authenticationEntryPoint;
	private final ApiAccessDeniedHandler accessDeniedHandler;
	private final JwtTokenProvider jwtTokenProvider;
	private final CorsConfigurationSource corsConfigurationSource;

	// 스웨거가 꺼진 환경(운영)에서는 경로도 허용하지 않는다
	@Value("${springdoc.api-docs.enabled:true}")
	private boolean swaggerEnabled;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			// 출처 허용은 CorsConfig. 인증보다 먼저 돌아 사전 요청(OPTIONS)은 토큰 없이 통과한다
			.cors(cors -> cors.configurationSource(corsConfigurationSource))
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// 캐시 헤더는 CacheControlFilter가 정한다 (갤러리 공개 캐시를 덮어쓰지 않게)
			.headers(headers -> headers.cacheControl(cache -> cache.disable()))
			.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class)
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.authorizeHttpRequests(auth -> {
				if (swaggerEnabled) {
					auth.requestMatchers(SWAGGER_PATHS).permitAll();
				}
				auth
					.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
					.requestMatchers(HttpMethod.GET, "/api/v1/gallery/**").permitAll()
					.requestMatchers("/api/v1/auth/**").permitAll()
					.anyRequest().authenticated();
			});
		return http.build();
	}

}
