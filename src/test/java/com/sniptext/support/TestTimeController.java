package com.sniptext.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sniptext.global.response.ApiResponse;

/** 시각 직렬화 검증용 컨트롤러. */
@TestComponent
@RestController
@RequestMapping("/api/v1/test/times")
public class TestTimeController {

	@GetMapping
	public ResponseEntity<ApiResponse<TimeResponse>> times() {
		return ApiResponse.success(TestSuccessCode.OK, new TimeResponse(
			LocalDateTime.of(2026, 10, 7, 10, 15, 0, 123_456_000),
			OffsetDateTime.of(2026, 10, 7, 1, 15, 0, 0, ZoneOffset.UTC),
			LocalDate.of(2026, 10, 14)
		));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<TimeRequest>> echo(@RequestBody TimeRequest request) {
		return ApiResponse.success(TestSuccessCode.OK, request);
	}

	public record TimeResponse(LocalDateTime localDateTime, OffsetDateTime offsetDateTime, LocalDate date) {
	}

	public record TimeRequest(LocalDateTime localDateTime, OffsetDateTime offsetDateTime) {
	}

}
