package com.sniptext.support;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sniptext.global.exception.BusinessException;
import com.sniptext.global.response.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 전역 예외 처리 검증용 컨트롤러. */
@TestComponent
@RestController
@RequestMapping("/api/v1/test")
public class TestExceptionController {

	@GetMapping("/business")
	public ResponseEntity<ApiResponse<Void>> business() {
		throw new BusinessException(TestErrorCode.CONFLICT);
	}

	@PostMapping("/validation")
	public ResponseEntity<ApiResponse<Void>> validation(@Valid @RequestBody ValidationRequest request) {
		return ApiResponse.success(TestSuccessCode.OK);
	}

	@GetMapping("/items/{itemId}")
	public ResponseEntity<ApiResponse<Long>> item(@PathVariable Long itemId) {
		return ApiResponse.success(TestSuccessCode.OK, itemId);
	}

	@GetMapping("/params")
	public ResponseEntity<ApiResponse<Integer>> params(
		@RequestParam @Min(value = 1, message = "TEST400_3") int size) {
		return ApiResponse.success(TestSuccessCode.OK, size);
	}

	@PostMapping("/unexpected")
	public ResponseEntity<ApiResponse<Void>> unexpected(@RequestBody String body) {
		throw new IllegalStateException("예상하지 못한 오류");
	}

	// 선언 순서: name → shortcut → memo → count
	public record ValidationRequest(
		@NotBlank(message = "TEST400_1") String name,
		@NotBlank(message = "TEST400_2") String shortcut,
		@Size(max = 3) String memo,
		@NotNull Integer count
	) {
	}

}
