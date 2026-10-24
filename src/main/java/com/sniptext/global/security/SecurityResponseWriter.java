package com.sniptext.global.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.sniptext.global.response.ApiResponse;
import com.sniptext.global.response.ResultCode;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/** 시큐리티 필터 단계의 에러를 공통 응답 형식으로 쓴다. */
@Component
@RequiredArgsConstructor
class SecurityResponseWriter {

	private final JsonMapper jsonMapper;

	void write(HttpServletResponse response, ResultCode resultCode) throws IOException {
		response.setStatus(resultCode.getHttpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setHeader("Cache-Control", "no-store");
		jsonMapper.writeValue(response.getOutputStream(), ApiResponse.of(resultCode));
	}

}
