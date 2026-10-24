package com.sniptext.global.response;

import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * 모든 API 응답의 공통 형식. 에러이거나 돌려줄 데이터가 없으면 result는 null이다.
 */
@JsonPropertyOrder({"isSuccess", "code", "message", "result"})
public record ApiResponse<T>(
	@JsonProperty("isSuccess") boolean isSuccess,
	String code,
	String message,
	T result
) {

	public static <T> ApiResponse<T> of(ResultCode resultCode, T result) {
		return new ApiResponse<>(
			resultCode.getHttpStatus().is2xxSuccessful(),
			resultCode.getCode(),
			resultCode.getMessage(),
			result
		);
	}

	public static <T> ApiResponse<T> of(ResultCode resultCode) {
		return of(resultCode, null);
	}

	/** 코드의 HTTP 상태(200, 201 등)로 성공 응답을 만든다. */
	public static <T> ResponseEntity<ApiResponse<T>> success(ResultCode resultCode, T result) {
		return ResponseEntity.status(resultCode.getHttpStatus()).body(of(resultCode, result));
	}

	public static <T> ResponseEntity<ApiResponse<T>> success(ResultCode resultCode) {
		return success(resultCode, null);
	}

	/** 에러 응답. result는 항상 null이다. */
	public static ResponseEntity<ApiResponse<Void>> error(ResultCode resultCode) {
		return ResponseEntity.status(resultCode.getHttpStatus()).body(of(resultCode));
	}

}
