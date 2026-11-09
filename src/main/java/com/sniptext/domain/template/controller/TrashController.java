package com.sniptext.domain.template.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sniptext.domain.template.controller.dto.TrashEmptyResponse;
import com.sniptext.domain.template.controller.dto.TrashListResponse;
import com.sniptext.domain.template.controller.dto.TrashPurgeResponse;
import com.sniptext.domain.template.controller.dto.TrashRestoreResponse;
import com.sniptext.domain.template.service.TrashCommandService;
import com.sniptext.domain.template.service.TrashQueryService;
import com.sniptext.domain.template.service.dto.command.TrashPurgeCommand;
import com.sniptext.domain.template.service.dto.command.TrashRestoreCommand;
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

	private final TrashCommandService trashCommandService;

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

	@PostMapping("/{templateId}/restore")
	public ResponseEntity<ApiResponse<TrashRestoreResponse>> restore(
		@LoginUser Long userId,
		@PathVariable Long templateId) {

		TrashRestoreResponse response = TrashRestoreResponse.from(
			trashCommandService.restore(new TrashRestoreCommand(userId, templateId)));

		return ApiResponse.success(TrashSuccessCode.TRASH_RESTORED, response);
	}

	@DeleteMapping("/{templateId}")
	public ResponseEntity<ApiResponse<TrashPurgeResponse>> purge(
		@LoginUser Long userId,
		@PathVariable Long templateId) {

		TrashPurgeResponse response = TrashPurgeResponse.from(
			trashCommandService.purge(new TrashPurgeCommand(userId, templateId)));

		return ApiResponse.success(TrashSuccessCode.TRASH_PURGED, response);
	}

	@DeleteMapping
	public ResponseEntity<ApiResponse<TrashEmptyResponse>> empty(@LoginUser Long userId) {
		TrashEmptyResponse response = TrashEmptyResponse.from(trashCommandService.empty(userId));

		return ApiResponse.success(TrashSuccessCode.TRASH_EMPTIED, response);
	}

}
