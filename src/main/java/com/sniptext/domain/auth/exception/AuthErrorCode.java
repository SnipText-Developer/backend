package com.sniptext.domain.auth.exception;

import org.springframework.http.HttpStatus;

import com.sniptext.global.response.ResultCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ResultCode {

	/** provider 값 오류 (S1은 GOOGLE만). 요청 객체의 검증 어노테이션이 코드로 쓴다 */
	UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "AUTH400_1", "지원하지 않는 로그인 방식이에요."),
	/** 인가 코드가 잘못됨·만료·이미 사용됨, 허용 목록에 없는 redirectUri, ID 토큰 검증 실패, 이메일 미인증 계정 */
	GOOGLE_LOGIN_REJECTED(HttpStatus.UNAUTHORIZED, "AUTH401_3", "구글 로그인 정보를 확인할 수 없어요. 다시 시도해주세요."),
	/** 구글 장애·연결 실패·시간 초과 */
	GOOGLE_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AUTH502_1", "일시적인 오류로 로그인하지 못했어요. 다시 시도해주세요.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

}
