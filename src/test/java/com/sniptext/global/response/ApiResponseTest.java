package com.sniptext.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.sniptext.global.exception.CommonErrorCode;

import tools.jackson.databind.json.JsonMapper;

class ApiResponseTest {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	@DisplayName("성공 응답은 코드의 HTTP 상태를 쓰고 isSuccess, code, message, result 순서로 직렬화된다")
	void success() {
		ResponseEntity<ApiResponse<String>> response = ApiResponse.success(TestSuccessCode.CREATED, "값");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(jsonMapper.writeValueAsString(response.getBody()))
			.isEqualTo("{\"isSuccess\":true,\"code\":\"TEST201_1\",\"message\":\"생성했어요.\",\"result\":\"값\"}");
	}

	@Test
	@DisplayName("돌려줄 데이터가 없는 성공 응답은 result가 null이다")
	void successWithoutResult() {
		ApiResponse<Void> body = ApiResponse.<Void>success(TestSuccessCode.CREATED).getBody();

		assertThat(body.isSuccess()).isTrue();
		assertThat(body.result()).isNull();
	}

	@Test
	@DisplayName("에러 응답은 isSuccess가 false이고 result가 null이다")
	void error() {
		ResponseEntity<ApiResponse<Void>> response = ApiResponse.error(CommonErrorCode.NOT_FOUND);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(jsonMapper.writeValueAsString(response.getBody()))
			.isEqualTo("{\"isSuccess\":false,\"code\":\"COMMON404_1\",\"message\":\"요청한 주소를 찾을 수 없어요.\",\"result\":null}");
	}

	private enum TestSuccessCode implements ResultCode {
		CREATED;

		@Override
		public HttpStatus getHttpStatus() {
			return HttpStatus.CREATED;
		}

		@Override
		public String getCode() {
			return "TEST201_1";
		}

		@Override
		public String getMessage() {
			return "생성했어요.";
		}
	}

}
