package com.sniptext.domain.template.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.sniptext.domain.template.entity.Shortcut;
import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.exception.TemplateErrorCode;
import com.sniptext.domain.template.exception.TrashErrorCode;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.template.service.dto.command.TrashRestoreCommand;
import com.sniptext.domain.template.service.dto.result.TemplateShortcutAssignResult;
import com.sniptext.domain.user.service.UserCommandService;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.global.exception.BusinessException;

/**
 * 한도 검사·자동 번호·저장 자체는 TemplateCommandServiceTest와 TrashRestoreControllerTest(실제 MySQL)에서 본다.
 * 활성·다른 회원 템플릿을 거르는 조건은 조회 쿼리에 있어 TrashTemplateRepositoryTest에서 본다.
 */
@ExtendWith(MockitoExtension.class)
class TrashCommandServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long TEMPLATE_ID = 18L;
	private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 10, 7, 10, 58);
	private static final TrashRestoreCommand COMMAND = new TrashRestoreCommand(USER_ID, TEMPLATE_ID);
	private static final Shortcut ORIGINAL = Shortcut.create(TemplateFixture.SHORTCUT);

	@Mock
	private TemplateRepository templateRepository;

	@Mock
	private UserCommandService userCommandService;

	@Mock
	private TemplateCommandService templateCommandService;

	@InjectMocks
	private TrashCommandService trashCommandService;

	private Template trashed;

	@BeforeEach
	void setUp() {
		trashed = TemplateFixture.trashedTemplate(USER_ID, TemplateFixture.SHORTCUT, DELETED_AT);
		ReflectionTestUtils.setField(trashed, "id", TEMPLATE_ID);
	}

	/** 잠금보다 먼저 일반 조회를 하면 그 스냅샷 때문에 잠금 뒤 개수 검사가 옛 값을 봐서 한도를 넘는다 */
	@Test
	@DisplayName("회원 잠금 → 템플릿 잠금 조회 → 한도 검사 → 단축어 고르기 → 저장 순서다")
	void order() {
		givenTrashed();
		givenAssigned(ORIGINAL);
		givenSaveSucceeds();

		trashCommandService.restore(COMMAND);

		InOrder order = inOrder(userCommandService, templateRepository, templateCommandService);
		order.verify(userCommandService).lockUser(USER_ID);
		order.verify(templateRepository).findTrashedByIdAndUserIdForUpdate(TEMPLATE_ID, USER_ID);
		order.verify(templateCommandService).validateTemplateLimit(USER_ID);
		order.verify(templateCommandService).assignShortcut(USER_ID, ORIGINAL);
		order.verify(templateCommandService).save(trashed);
	}

	@Test
	@DisplayName("겹치지 않으면 삭제 당시 단축어 그대로 휴지통에서 꺼내고 바뀌지 않았다고 알려 준다")
	void restoreWithOriginal() {
		givenTrashed();
		givenAssigned(ORIGINAL);
		givenSaveSucceeds();

		TemplateShortcutAssignResult result = trashCommandService.restore(COMMAND);

		assertThat(trashed.isInTrash()).isFalse();
		assertThat(result).isEqualTo(new TemplateShortcutAssignResult(
			TEMPLATE_ID, TemplateFixture.TITLE, "/견적", "/견적", false));
	}

	@Test
	@DisplayName("겹쳐서 번호가 붙으면 번호 단축어로 꺼내고 원래 단축어와 바뀐 사실을 알려 준다")
	void restoreWithNumbered() {
		givenTrashed();
		givenAssigned(Shortcut.create("/견적2"));
		givenSaveSucceeds();

		TemplateShortcutAssignResult result = trashCommandService.restore(COMMAND);

		assertThat(trashed.getShortcut().getKey()).isEqualTo("/견적2");
		assertThat(result).isEqualTo(new TemplateShortcutAssignResult(
			TEMPLATE_ID, TemplateFixture.TITLE, "/견적2", "/견적", true));
	}

	@Test
	@DisplayName("본인 휴지통에서 찾지 못하면 TRASH404_1이고 한도 검사·저장을 하지 않는다")
	void notFound() {
		given(templateRepository.findTrashedByIdAndUserIdForUpdate(TEMPLATE_ID, USER_ID)).willReturn(Optional.empty());

		assertErrorCode(TrashErrorCode.TRASHED_TEMPLATE_NOT_FOUND);

		then(templateCommandService).should(never()).validateTemplateLimit(anyLong());
		then(templateCommandService).should(never()).save(any());
	}

	@Test
	@DisplayName("한도에 도달했으면 TEMPLATE403_1이고 휴지통에 그대로 둔다")
	void limitReached() {
		givenTrashed();
		willThrow(new BusinessException(TemplateErrorCode.TEMPLATE_LIMIT_REACHED))
			.given(templateCommandService).validateTemplateLimit(USER_ID);

		assertErrorCode(TemplateErrorCode.TEMPLATE_LIMIT_REACHED);

		assertThat(trashed.isInTrash()).isTrue();
		then(templateCommandService).should(never()).assignShortcut(anyLong(), any());
		then(templateCommandService).should(never()).save(any());
	}

	@Test
	@DisplayName("번호 2~99가 모두 차면 TEMPLATE409_2이고 휴지통에 그대로 둔다")
	void numbersExhausted() {
		givenTrashed();
		given(templateCommandService.assignShortcut(USER_ID, ORIGINAL))
			.willThrow(new BusinessException(TemplateErrorCode.SHORTCUT_NUMBER_EXHAUSTED));

		assertErrorCode(TemplateErrorCode.SHORTCUT_NUMBER_EXHAUSTED);

		assertThat(trashed.isInTrash()).isTrue();
		then(templateCommandService).should(never()).save(any());
	}

	private void givenTrashed() {
		given(templateRepository.findTrashedByIdAndUserIdForUpdate(TEMPLATE_ID, USER_ID)).willReturn(Optional.of(trashed));
	}

	private void givenAssigned(Shortcut assigned) {
		given(templateCommandService.assignShortcut(USER_ID, ORIGINAL)).willReturn(assigned);
	}

	private void givenSaveSucceeds() {
		given(templateCommandService.save(trashed)).willReturn(trashed);
	}

	private void assertErrorCode(Object resultCode) {
		assertThatThrownBy(() -> trashCommandService.restore(COMMAND))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(resultCode);
	}

}
