package com.sniptext.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;

import com.sniptext.support.IntegrationTestSupport;

@WithMockUser
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest extends IntegrationTestSupport {

	@Test
	@DisplayName("도메인 예외는 담긴 결과 코드의 HTTP 상태와 코드로 응답한다")
	void businessException() throws Exception {
		expectError(mockMvc.perform(get("/api/v1/test/business")), 409, "TEST409_1")
			.andExpect(jsonPath("$.message").value("이미 있어요."));
	}

	@RepeatedTest(5)
	@DisplayName("검증 에러가 여러 개면 필드 선언 순서상 첫 번째 필드의 코드 하나만 응답한다")
	void firstValidationErrorByDeclarationOrder() throws Exception {
		expectError(postJson("/api/v1/test/validation", "{\"memo\":\"너무 긴 메모\"}"), 400, "TEST400_1")
			.andExpect(jsonPath("$.message").value("이름을 입력해주세요."));
		expectError(postJson("/api/v1/test/validation", "{\"name\":\"이름\"}"), 400, "TEST400_2");
	}

	@Test
	@DisplayName("검증 어노테이션 message에 결과 코드가 없으면 COMMON400_1로 응답한다")
	void validationWithoutCode() throws Exception {
		expectError(postJson("/api/v1/test/validation",
			"{\"name\":\"이름\",\"shortcut\":\"/인사\",\"memo\":\"너무 긴 메모\",\"count\":1}"), 400, "COMMON400_1")
			.andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
	}

	@Test
	@DisplayName("요청 파라미터 검증 실패도 message의 결과 코드로 응답한다")
	void parameterValidation() throws Exception {
		expectError(mockMvc.perform(get("/api/v1/test/params").param("size", "0")), 400, "TEST400_3");
	}

	@Test
	@DisplayName("JSON 파싱 실패, 타입 불일치, 필수 값 누락은 COMMON400_1로 응답한다")
	void malformedRequests() throws Exception {
		expectError(postJson("/api/v1/test/validation", "{\"name\":"), 400, "COMMON400_1");
		expectError(postJson("/api/v1/test/validation",
			"{\"name\":\"이름\",\"shortcut\":\"/인사\",\"count\":\"숫자아님\"}"), 400, "COMMON400_1");
		expectError(mockMvc.perform(get("/api/v1/test/items/abc")), 400, "COMMON400_1");
		expectError(mockMvc.perform(get("/api/v1/test/params")), 400, "COMMON400_1");
		expectError(mockMvc.perform(post("/api/v1/test/validation")
			.contentType(MediaType.TEXT_PLAIN).content("text")), 400, "COMMON400_1");
	}

	@Test
	@DisplayName("없는 경로는 COMMON404_1, 지원하지 않는 메서드는 COMMON405_1로 응답한다")
	void notFoundAndMethodNotAllowed() throws Exception {
		expectError(mockMvc.perform(get("/api/v1/test/no-such-path")), 404, "COMMON404_1");
		expectError(mockMvc.perform(delete("/api/v1/test/ping")), 405, "COMMON405_1");
	}

	@Test
	@DisplayName("예상하지 못한 예외는 COMMON500_1로 응답하고, 로그에 예외는 남기되 요청 본문은 남기지 않는다")
	void unexpectedException(CapturedOutput output) throws Exception {
		expectError(postJson("/api/v1/test/unexpected", "{\"content\":\"비밀본문-1234\"}"), 500, "COMMON500_1")
			.andExpect(jsonPath("$.message").value("서버에 문제가 생겼어요. 잠시 후 다시 시도해주세요."));

		assertThat(output).contains("처리하지 못한 예외: POST /api/v1/test/unexpected", "IllegalStateException");
		assertThat(output).doesNotContain("비밀본문-1234");
	}

	private ResultActions postJson(String path, String body) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static ResultActions expectError(ResultActions actions, int status, String code) throws Exception {
		return actions
			.andExpect(status().is(status))
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.result").isEmpty());
	}

}
