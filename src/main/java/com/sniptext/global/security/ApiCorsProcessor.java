package com.sniptext.global.security;

import java.io.IOException;

import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.DefaultCorsProcessor;

import com.sniptext.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 허용하지 않은 출처의 요청을 공통 형식의 COMMON403_1로 거절한다.
 * 기본 처리는 본문이 "Invalid CORS request"이고, 앞서 CacheControlFilter가 넣은 갤러리 공개 캐시 헤더가
 * 그대로 남는다 (전역 예외 처리를 거치지 않는다). 공통 응답 작성 도구가 no-store로 바꿔 쓴다.
 */
@Component
@RequiredArgsConstructor
class ApiCorsProcessor extends DefaultCorsProcessor {

	private final SecurityResponseWriter responseWriter;

	@Override
	protected void rejectRequest(ServerHttpResponse response) throws IOException {
		// DefaultCorsProcessor는 항상 서블릿 응답을 감싸서 넘긴다
		responseWriter.write(((ServletServerHttpResponse)response).getServletResponse(), CommonErrorCode.FORBIDDEN);
	}

}
