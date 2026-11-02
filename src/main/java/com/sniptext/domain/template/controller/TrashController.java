package com.sniptext.domain.template.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sniptext.domain.template.controller.dto.TrashListResponse;
import com.sniptext.domain.template.service.TrashQueryService;
import com.sniptext.domain.template.service.dto.query.TrashListQuery;
import com.sniptext.global.response.ApiResponse;
import com.sniptext.global.security.LoginUser;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * 휴지통 (로그인 필요, 캐시 금지).
 */
@RestController
@RequestMapping("/api/v1/trash/templates")
@RequiredArgsConstructor
public class TrashController {

	private final TrashQueryService trashQueryService;

	@GetMapping
	public ResponseEntity<ApiResponse<TrashListResponse>> getTrash(
		@LoginUser Long userId,
		@RequestParam(required = false) String cursor,
		@RequestParam(defaultValue = "20")
		@Min(value = 1, message = "TRASH400_1") @Max(value = 50, message = "TRASH400_1") int size) {

		TrashListResponse response = TrashListResponse.from(
			trashQueryService.getTrash(new TrashListQuery(userId, cursor, size)));

		return ApiResponse.success(TrashSuccessCode.TRASH_LIST_FOUND, response);
	}

}
