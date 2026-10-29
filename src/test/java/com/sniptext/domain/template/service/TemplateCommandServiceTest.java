package com.sniptext.domain.template.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
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

import com.sniptext.domain.plan.service.PlanQueryService;
import com.sniptext.domain.plan.service.dto.result.PlanResult;
import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.exception.TemplateErrorCode;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.template.service.dto.command.TemplateCreateCommand;
import com.sniptext.domain.template.service.dto.result.TemplateCreateResult;
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
	@DisplayName("회원 행을 잠근 뒤에 플랜 한도와 개수를 확인하고 저장한다")
	void locksBeforeLimitCheck() {
		givenActiveCount(FREE, 0);
		givenSaveSucceeds();

		templateCommandService.create(COMMAND);

		InOrder inOrder = inOrder(userCommandService, planQueryService, templateRepository);
		inOrder.verify(userCommandService).lockUser(USER_ID);
		inOrder.verify(planQueryService).getCurrentPlan(USER_ID);
		inOrder.verify(templateRepository).countByUserIdAndDeletedAtIsNull(USER_ID);
		inOrder.verify(templateRepository).saveAndFlush(any());
	}

	@Test
	@DisplayName("활성 단축어 유니크 위반은 TEMPLATE409_1로 바뀌고, 예외 메시지에 겹친 단축어가 남지 않는다")
	void duplicateShortcut() {
		givenActiveCount(FREE, 0);
		givenSaveFails("templates.uk_templates_user_active_shortcut");

		assertThatThrownBy(() -> templateCommandService.create(COMMAND))
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
		givenSaveFails("fk_templates_user");

		assertThatThrownBy(() -> templateCommandService.create(COMMAND))
			.isInstanceOf(DataIntegrityViolationException.class);
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

	private void givenSaveFails(String constraintName) {
		ConstraintViolationException cause = new ConstraintViolationException(
			"Duplicate entry '1-/quote' for key '" + constraintName + "'", new SQLException(), constraintName);
		given(templateRepository.saveAndFlush(any(Template.class)))
			.willThrow(new DataIntegrityViolationException(cause.getMessage(), cause));
	}

}
