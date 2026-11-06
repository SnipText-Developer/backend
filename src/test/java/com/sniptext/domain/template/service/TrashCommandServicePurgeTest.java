package com.sniptext.domain.template.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sniptext.domain.template.exception.TrashErrorCode;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.template.service.dto.command.TrashPurgeCommand;
import com.sniptext.domain.template.service.dto.result.TrashEmptyResult;
import com.sniptext.domain.template.service.dto.result.TrashPurgeResult;
import com.sniptext.domain.user.service.UserCommandService;
import com.sniptext.global.exception.BusinessException;

/**
 * 영구 삭제·비우기는 엔티티를 읽지 않고 삭제 쿼리의 지운 행 수만 본다.
 * 본인 소유·휴지통 조건은 쿼리에 있어 TrashPurgeRepositoryTest에서 본다.
 * 회원 잠금 순서가 지켜지는지(복구와 교착하지 않는지)는 TrashPurgeConcurrencyTest에서 실제 MySQL로 본다.
 */
@ExtendWith(MockitoExtension.class)
class TrashCommandServicePurgeTest {

	private static final Long USER_ID = 1L;
	private static final Long TEMPLATE_ID = 18L;
	private static final TrashPurgeCommand COMMAND = new TrashPurgeCommand(USER_ID, TEMPLATE_ID);

	@Mock
	private TemplateRepository templateRepository;

	@Mock
	private UserCommandService userCommandService;

	@Mock
	private TemplateCommandService templateCommandService;

	@InjectMocks
	private TrashCommandService trashCommandService;

	@Test
	@DisplayName("영구 삭제는 엔티티를 읽지 않고 지우며, 1행을 지웠으면 그 ID를 돌려준다")
	void purge() {
		given(templateRepository.deleteTrashedByIdAndUserId(TEMPLATE_ID, USER_ID)).willReturn(1);

		TrashPurgeResult result = trashCommandService.purge(COMMAND);

		assertThat(result).isEqualTo(new TrashPurgeResult(TEMPLATE_ID));
		then(templateRepository).should().deleteTrashedByIdAndUserId(TEMPLATE_ID, USER_ID);
		then(templateRepository).shouldHaveNoMoreInteractions();
	}

	/** 복구와 같은 순서(회원 행 → 템플릿 행)로 잠가야 교착이 나지 않는다 */
	@Test
	@DisplayName("영구 삭제는 회원을 먼저 잠근 뒤 지운다")
	void purgeLocksUserFirst() {
		given(templateRepository.deleteTrashedByIdAndUserId(TEMPLATE_ID, USER_ID)).willReturn(1);

		trashCommandService.purge(COMMAND);

		InOrder order = inOrder(userCommandService, templateRepository);
		order.verify(userCommandService).lockUser(USER_ID);
		order.verify(templateRepository).deleteTrashedByIdAndUserId(TEMPLATE_ID, USER_ID);
	}

	@Test
	@DisplayName("지운 행이 없으면 TRASH404_1이다")
	void purgeNotFound() {
		given(templateRepository.deleteTrashedByIdAndUserId(TEMPLATE_ID, USER_ID)).willReturn(0);

		assertThatThrownBy(() -> trashCommandService.purge(COMMAND))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(TrashErrorCode.TRASHED_TEMPLATE_NOT_FOUND);
	}

	@Test
	@DisplayName("비우기는 엔티티를 읽지 않고 지우며, 지운 개수를 돌려준다")
	void empty() {
		given(templateRepository.deleteTrashedByUserId(USER_ID)).willReturn(4);

		TrashEmptyResult result = trashCommandService.empty(USER_ID);

		assertThat(result).isEqualTo(new TrashEmptyResult(4));
		then(templateRepository).should().deleteTrashedByUserId(USER_ID);
		then(templateRepository).shouldHaveNoMoreInteractions();
	}

	@Test
	@DisplayName("비우기는 회원을 먼저 잠근 뒤 지운다")
	void emptyLocksUserFirst() {
		trashCommandService.empty(USER_ID);

		InOrder order = inOrder(userCommandService, templateRepository);
		order.verify(userCommandService).lockUser(USER_ID);
		order.verify(templateRepository).deleteTrashedByUserId(USER_ID);
	}

	@Test
	@DisplayName("비울 것이 없어도 에러가 아니라 0개다")
	void emptyNothing() {
		given(templateRepository.deleteTrashedByUserId(USER_ID)).willReturn(0);

		assertThat(trashCommandService.empty(USER_ID)).isEqualTo(new TrashEmptyResult(0));
	}

}
