package com.sniptext.domain.user.controller;

import org.springframework.http.ResponseEntity;

import com.sniptext.domain.user.controller.dto.UserDetailResponse;
import com.sniptext.domain.user.controller.dto.UserUsageResponse;
import com.sniptext.global.response.ApiResponse;
import com.sniptext.global.security.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 회원 API의 스웨거 설명. 매핑은 {@link UserController}에 있다.
 * 자세한 규칙은 docs/s1-api-spec.md 5·6번이 기준이다.
 */
@Tag(name = "회원", description = "내 정보와 템플릿·AI 톤 교정 사용량 조회")
public interface UserControllerDocs {

	@Operation(summary = "내 정보 조회",
		description = "사이드바 프로필과 계정 팝오버에 씁니다. 탈퇴했거나 없는 회원의 토큰이면 USER404_1입니다.")
	ResponseEntity<ApiResponse<UserDetailResponse>> getMe(@LoginUser Long userId);

	@Operation(summary = "사용량 조회",
		description = "사이드바 개수 뱃지와 AI 톤 교정 버튼 라벨(사용 횟수/한도)에 씁니다. "
			+ "템플릿은 휴지통을 뺀 개수와 한도 50개, AI는 오늘(한국 시간) 사용 횟수와 하루 한도 5회이며 resetAt이 다음 초기화 시각입니다.")
	ResponseEntity<ApiResponse<UserUsageResponse>> getUsage(@LoginUser Long userId);

}
