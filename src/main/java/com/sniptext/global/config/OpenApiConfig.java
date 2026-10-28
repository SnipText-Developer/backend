package com.sniptext.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * 스웨거 문서 설정. Authorize 버튼에 접근 토큰(로컬은 개발용 임시 토큰)을 넣으면 모든 요청에 Bearer 헤더가 붙는다.
 * 운영은 스웨거 자체가 꺼져 있다.
 */
@Configuration
public class OpenApiConfig {

	private static final String BEARER_SCHEME = "bearerAuth";

	@Bean
	public OpenAPI openApi() {
		return new OpenAPI()
			.info(new Info().title("SNIPTEXT API").version("v1"))
			.components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
				.type(SecurityScheme.Type.HTTP)
				.scheme("bearer")
				.bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
	}

}
