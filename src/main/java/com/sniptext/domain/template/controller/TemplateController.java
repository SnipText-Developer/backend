package com.sniptext.domain.template.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sniptext.domain.template.controller.dto.TemplateCreateRequest;
import com.sniptext.domain.template.controller.dto.TemplateCreateResponse;
import com.sniptext.domain.template.service.TemplateCommandService;
import com.sniptext.domain.template.service.dto.result.TemplateCreateResult;
import com.sniptext.global.response.ApiResponse;
import com.sniptext.global.security.LoginUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 내 템플릿 (로그인 필요, 캐시 금지).
 */
@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
public class TemplateController {

	private final TemplateCommandService templateCommandService;

	@PostMapping
	public ResponseEntity<ApiResponse<TemplateCreateResponse>> createTemplate(
		@LoginUser Long userId,
		@Valid @RequestBody TemplateCreateRequest request) {

		TemplateCreateResult result = templateCommandService.create(request.toCommand(userId));

		return ApiResponse.success(TemplateSuccessCode.TEMPLATE_CREATED, TemplateCreateResponse.from(result));
	}

}
