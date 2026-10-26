package com.sniptext.global.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.sniptext.global.exception.CommonErrorCode;
import com.sniptext.global.response.ResultCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 인증이 필요한 경로에 인증 정보가 없을 때 응답한다.
 * 토큰 필터가 남긴 사유가 있으면 그 코드(만료 COMMON401_1, 위조·형식 오류 COMMON401_2), 토큰이 없으면 COMMON401_1.
 */
@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final SecurityResponseWriter responseWriter;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
		AuthenticationException authException) throws IOException {
		Object tokenError = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
		ResultCode resultCode = tokenError instanceof ResultCode code ? code : CommonErrorCode.UNAUTHORIZED;
		responseWriter.write(response, resultCode);
	}

}
