package com.sniptext.domain.template.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.sniptext.domain.plan.service.PlanQueryService;
import com.sniptext.domain.plan.service.dto.result.PlanResult;
import com.sniptext.domain.template.entity.Shortcut;
import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.exception.TemplateErrorCode;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.template.service.dto.command.TemplateCreateCommand;
import com.sniptext.domain.template.service.dto.command.TemplateCreateFromGalleryCommand;
import com.sniptext.domain.template.service.dto.result.TemplateCreateResult;
import com.sniptext.domain.template.service.dto.result.TemplateShortcutAssignResult;
import com.sniptext.domain.user.entity.Plan;
import com.sniptext.domain.user.service.UserCommandService;
import com.sniptext.global.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class TemplateCommandServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long TEMPLATE_ID = 12L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 10, 30);
	private static final PlanResult FREE = new PlanResult(Plan.FREE, 5, 0);
	private static final String CONTENT = "안녕하세요 {{고객명}}님,\n견적은 {{금액}}원입니다. {{고객명}}님 {{고객 명}}";
	private static final TemplateCreateCommand COMMAND =
		new TemplateCreateCommand(USER_ID, "  견적 안내  ", "/Quote", CONTENT);

	@Mock
	private TemplateRepository templateRepository;

	@Mock
	private UserCommandService userCommandService;

	@Mock
	private PlanQueryService planQueryService;

	@Mock
	private TransactionTemplate transactionTemplate;

	@InjectMocks
	private TemplateCommandService templateCommandService;

	@Test
	@DisplayName("값 객체 규칙을 거쳐 저장하고, 저장된 값과 첫 등장 순·중복 제거한 변수를 돌려준다")
	void create() {
		givenActiveCount(FREE, 0);
		givenSaveSucceeds();

		TemplateCreateResult result = templateCommandService.create(COMMAND);

		ArgumentCaptor<Template> saved = ArgumentCaptor.forClass(Template.class);
		then(templateRepository).should().saveAndFlush(saved.capture());
		assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
		assertThat(saved.getValue().getShortcut().getKey()).isEqualTo("/quote");
		assertThat(result).isEqualTo(new TemplateCreateResult(TEMPLATE_ID, "견적 안내", "/Quote", CONTENT,
			List.of("고객명", "금액"), null, NOW, NOW));
	}

	@ParameterizedTest
	@CsvSource({"FREE, 5, 4", "PRO, 50, 49"})
	@DisplayName("활성 개수가 한도보다 하나 적으면 저장한다")
	void belowLimit(Plan plan, int limit, long activeCount) {
		givenActiveCount(new PlanResult(plan, limit, 0), activeCount);
		givenSaveSucceeds();

		TemplateCreateResult result = templateCommandService.create(COMMAND);

		assertThat(result.templateId()).isEqualTo(TEMPLATE_ID);
	}

	@ParameterizedTest
	@CsvSource({"FREE, 5, 5", "PRO, 50, 50"})
	@DisplayName("활성 개수가 한도에 도달했으면 TEMPLATE403_1이고 저장하지 않는다")
	void limitReached(Plan plan, int limit, long activeCount) {
		givenActiveCount(new PlanResult(plan, limit, 0), activeCount);

		assertThatThrownBy(() -> templateCommandService.create(COMMAND))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(TemplateErrorCode.TEMPLATE_LIMIT_REACHED);
		then(templateRepository).should(never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("회원 행을 잠근 뒤에 플랜 한도와 개수, 단축어 중복을 확인하고 저장한다")
	void locksBeforeLimitCheck() {
		givenActiveCount(FREE, 0);
		givenSaveSucceeds();

		templateCommandService.create(COMMAND);

		InOrder inOrder = inOrder(userCommandService, planQueryService, templateRepository);
		inOrder.verify(userCommandService).lockUser(USER_ID);
		inOrder.verify(planQueryService).getCurrentPlan(USER_ID);
		inOrder.verify(templateRepository).countByUserIdAndDeletedAtIsNull(USER_ID);
		inOrder.verify(templateRepository).existsByUserIdAndActiveShortcutKey(USER_ID, "/quote");
		inOrder.verify(templateRepository).saveAndFlush(any());
	}

	@Test
	@DisplayName("활성 템플릿에 같은 단축어 키가 있으면 저장 전에 TEMPLATE409_1이고 저장하지 않는다")
	void duplicateShortcutBeforeSave() {
		givenActiveCount(FREE, 0);
		given(templateRepository.existsByUserIdAndActiveShortcutKey(USER_ID, "/quote")).willReturn(true);

		assertThatThrownBy(() -> templateCommandService.create(COMMAND))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(TemplateErrorCode.SHORTCUT_DUPLICATED);
		then(templateRepository).should(never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("미리 조회를 통과했어도 저장에서 활성 단축어 유니크 위반이 나면 TEMPLATE409_1이고, 예외 메시지에 겹친 단축어가 남지 않는다")
	void duplicateShortcut() {
		givenActiveCount(FREE, 0);
		givenSaveFails(ConstraintKind.UNIQUE, "templates.uk_templates_user_active_shortcut");

		assertThatThrownBy(() -> templateCommandService.create(COMMAND))
			.isInstanceOf(BusinessException.class)
			.hasMessage("TEMPLATE409_1")
			.hasNoCause()
			.extracting("resultCode")
			.isEqualTo(TemplateErrorCode.SHORTCUT_DUPLICATED);
	}

	@ParameterizedTest
	@CsvSource({
		"FOREIGN_KEY, templates.fk_templates_user",
		"UNIQUE,      templates.xuk_templates_user_active_shortcut",
		"OTHER,       templates.uk_templates_user_active_shortcut"
	})
	@DisplayName("단축어 유니크(종류 UNIQUE, 이름 정확히 일치)가 아닌 무결성 위반은 바꾸지 않고 그대로 던진다 (코드 실수라 500)")
	void otherIntegrityViolation(ConstraintKind kind, String constraintName) {
		givenActiveCount(FREE, 0);
		givenSaveFails(kind, constraintName);

		assertThatThrownBy(() -> templateCommandService.create(COMMAND))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Nested
	@DisplayName("갤러리에서 저장")
	class CreateFromGallery {

		private static final Long GALLERY_TEMPLATE_ID = 7L;
		private static final String TITLE = "프리랜서 견적 안내";
		private static final String SHORTCUT = "/견적";
		private static final TemplateCreateFromGalleryCommand GALLERY_COMMAND =
			new TemplateCreateFromGalleryCommand(USER_ID, GALLERY_TEMPLATE_ID, TITLE, SHORTCUT, CONTENT);

		/** 트랜잭션 경계는 TemplateCreateFromGalleryTest가 실제로 검증한다. 여기서는 콜백만 실행한다 */
		@BeforeEach
		void setUp() {
			given(transactionTemplate.execute(any())).willAnswer(invocation ->
				invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
		}

		@Test
		@DisplayName("충돌이 없으면 원본 단축어로 출처와 함께 저장하고 shortcutChanged는 false다")
		void noConflict() {
			givenActiveCount(FREE, 0);
			givenUsedKeys(GALLERY_COMMAND.shortcut());
			givenSaveSucceeds();

			TemplateShortcutAssignResult result = templateCommandService.createFromGallery(GALLERY_COMMAND);

			ArgumentCaptor<Template> saved = ArgumentCaptor.forClass(Template.class);
			then(templateRepository).should().saveAndFlush(saved.capture());
			assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
			assertThat(saved.getValue().getSourceGalleryTemplateId()).isEqualTo(GALLERY_TEMPLATE_ID);
			assertThat(saved.getValue().getContent().getValue()).isEqualTo(CONTENT);
			assertThat(result).isEqualTo(new TemplateShortcutAssignResult(TEMPLATE_ID, TITLE, SHORTCUT, SHORTCUT, false));
		}

		@Test
		@DisplayName("충돌이 있으면 비어 있는 첫 번호를 붙여 저장하고 shortcutChanged는 true다")
		void conflict() {
			givenActiveCount(FREE, 0);
			givenUsedKeys(GALLERY_COMMAND.shortcut(), "/견적", "/견적2");
			givenSaveSucceeds();

			TemplateShortcutAssignResult result = templateCommandService.createFromGallery(GALLERY_COMMAND);

			assertThat(result).isEqualTo(new TemplateShortcutAssignResult(TEMPLATE_ID, TITLE, "/견적3", SHORTCUT, true));
		}

		@Test
		@DisplayName("자모 분리형 원본은 글자가 같으므로 충돌이 없으면 shortcutChanged가 false이고 원본도 NFC로 돌려준다")
		void decomposedOriginalIsNotChanged() {
			String decomposed = Normalizer.normalize(SHORTCUT, Normalizer.Form.NFD);
			givenActiveCount(FREE, 0);
			givenUsedKeys(decomposed);
			givenSaveSucceeds();

			TemplateShortcutAssignResult result = templateCommandService.createFromGallery(
				new TemplateCreateFromGalleryCommand(USER_ID, GALLERY_TEMPLATE_ID, TITLE, decomposed, CONTENT));

			assertThat(result.shortcut()).isEqualTo(SHORTCUT);
			assertThat(result.originalShortcut()).isEqualTo(SHORTCUT);
			assertThat(result.shortcutChanged()).isFalse();
		}

		@Test
		@DisplayName("활성 개수가 한도에 도달했으면 TEMPLATE403_1이고 단축어 조회와 저장을 하지 않는다")
		void limitReached() {
			givenActiveCount(FREE, 5);

			assertThatThrownBy(() -> templateCommandService.createFromGallery(GALLERY_COMMAND))
				.isInstanceOf(BusinessException.class)
				.extracting("resultCode")
				.isEqualTo(TemplateErrorCode.TEMPLATE_LIMIT_REACHED);
			then(templateRepository).should(never()).findActiveShortcutKeys(any(), any());
			then(templateRepository).should(never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("후보가 모두 차면 TEMPLATE409_2이고 저장하지 않는다")
		void numbersExhausted() {
			givenActiveCount(FREE, 0);
			givenUsedKeys(GALLERY_COMMAND.shortcut(),
				Shortcut.create(GALLERY_COMMAND.shortcut()).candidateKeys().toArray(String[]::new));

			assertThatThrownBy(() -> templateCommandService.createFromGallery(GALLERY_COMMAND))
				.isInstanceOf(BusinessException.class)
				.extracting("resultCode")
				.isEqualTo(TemplateErrorCode.SHORTCUT_NUMBER_EXHAUSTED);
			then(templateRepository).should(never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("회원 행을 잠근 뒤에 한도, 사용 중인 키를 확인하고 저장한다")
		void locksBeforeLimitCheck() {
			givenActiveCount(FREE, 0);
			givenUsedKeys(GALLERY_COMMAND.shortcut());
			givenSaveSucceeds();

			templateCommandService.createFromGallery(GALLERY_COMMAND);

			InOrder inOrder = inOrder(userCommandService, planQueryService, templateRepository);
			inOrder.verify(userCommandService).lockUser(USER_ID);
			inOrder.verify(planQueryService).getCurrentPlan(USER_ID);
			inOrder.verify(templateRepository).countByUserIdAndDeletedAtIsNull(USER_ID);
			inOrder.verify(templateRepository).findActiveShortcutKeys(any(), any());
			inOrder.verify(templateRepository).saveAndFlush(any());
		}

		@Test
		@DisplayName("미리 조회를 통과했어도 저장에서 활성 단축어 유니크 위반이 나면 TEMPLATE409_1이고, 예외 메시지에 겹친 단축어가 남지 않는다")
		void duplicateShortcut() {
			givenActiveCount(FREE, 0);
			givenUsedKeys(GALLERY_COMMAND.shortcut());
			givenSaveFails(ConstraintKind.UNIQUE, "templates.uk_templates_user_active_shortcut");

			assertThatThrownBy(() -> templateCommandService.createFromGallery(GALLERY_COMMAND))
				.isInstanceOf(BusinessException.class)
				.hasMessage("TEMPLATE409_1")
				.hasNoCause()
				.extracting("resultCode")
				.isEqualTo(TemplateErrorCode.SHORTCUT_DUPLICATED);
		}

		@Test
		@DisplayName("단축어 유니크가 아닌 무결성 위반은 바꾸지 않고 그대로 던진다 (코드 실수라 500)")
		void otherIntegrityViolation() {
			givenActiveCount(FREE, 0);
			givenUsedKeys(GALLERY_COMMAND.shortcut());
			givenSaveFails(ConstraintKind.FOREIGN_KEY, "templates.fk_templates_gallery");

			assertThatThrownBy(() -> templateCommandService.createFromGallery(GALLERY_COMMAND))
				.isInstanceOf(DataIntegrityViolationException.class);
		}

		/** 원본의 후보 키 전체로 조회해야 이미 쓰는 키가 돌아온다 */
		private void givenUsedKeys(String shortcut, String... usedKeys) {
			given(templateRepository.findActiveShortcutKeys(USER_ID, Shortcut.create(shortcut).candidateKeys()))
				.willReturn(Set.of(usedKeys));
		}

	}

	private void givenActiveCount(PlanResult plan, long activeCount) {
		given(planQueryService.getCurrentPlan(USER_ID)).willReturn(plan);
		given(templateRepository.countByUserIdAndDeletedAtIsNull(USER_ID)).willReturn(activeCount);
	}

	/** 저장하면 DB가 채우는 ID와 시각을 흉내 낸다 */
	private void givenSaveSucceeds() {
		given(templateRepository.saveAndFlush(any(Template.class))).willAnswer(invocation -> {
			Template template = invocation.getArgument(0);
			ReflectionTestUtils.setField(template, "id", TEMPLATE_ID);
			ReflectionTestUtils.setField(template, "createdAt", NOW);
			ReflectionTestUtils.setField(template, "editedAt", NOW);
			return template;
		});
	}

	/** 실제 MySQL·하이버네이트가 넘기는 모양(TemplateRepositoryTest에서 고정)대로 위반 예외를 만든다 */
	private void givenSaveFails(ConstraintKind kind, String constraintName) {
		ConstraintViolationException cause = new ConstraintViolationException(
			"Duplicate entry '1-/quote' for key '" + constraintName + "'", new SQLException(), kind, constraintName);
		given(templateRepository.saveAndFlush(any(Template.class)))
			.willThrow(new DataIntegrityViolationException(cause.getMessage(), cause));
	}

}
