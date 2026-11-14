package com.sniptext.domain.template.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.exception.TemplateErrorCode;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.template.service.dto.query.TemplateDetailQuery;
import com.sniptext.domain.template.service.dto.result.TemplateDetailResult;
import com.sniptext.domain.template.service.dto.result.TemplateListItemResult;
import com.sniptext.domain.template.service.dto.result.TemplateListResult;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.global.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class TemplateQueryServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long TEMPLATE_ID = 12L;
	private static final TemplateDetailQuery QUERY = new TemplateDetailQuery(USER_ID, TEMPLATE_ID);

	@Mock
	private TemplateRepository templateRepository;

	@InjectMocks
	private TemplateQueryService templateQueryService;

	@Test
	@DisplayName("보유 개수는 휴지통을 제외한 개수다")
	void countActive() {
		given(templateRepository.countByUserIdAndDeletedAtIsNull(1L)).willReturn(3L);

		assertThat(templateQueryService.countActive(1L)).isEqualTo(3L);
	}

	@Test
	@DisplayName("상세 조회는 본문에서 뽑은 변수 목록을 함께 돌려준다")
	void getTemplate() {
		given(templateRepository.findByIdAndUserId(TEMPLATE_ID, USER_ID))
			.willReturn(Optional.of(TemplateFixture.template(USER_ID)));

		TemplateDetailResult result = templateQueryService.getTemplate(QUERY);

		assertThat(result.title()).isEqualTo(TemplateFixture.TITLE);
		assertThat(result.shortcut()).isEqualTo(TemplateFixture.SHORTCUT);
		assertThat(result.content()).isEqualTo(TemplateFixture.CONTENT);
		assertThat(result.variables()).containsExactly("고객명");
	}

	@Test
	@DisplayName("본인 소유로 찾지 못하면 TEMPLATE404_1")
	void getTemplateNotFound() {
		given(templateRepository.findByIdAndUserId(TEMPLATE_ID, USER_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> templateQueryService.getTemplate(QUERY))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(TemplateErrorCode.TEMPLATE_NOT_FOUND);
	}

	@Test
	@DisplayName("내 템플릿이 휴지통에 있으면 TEMPLATE404_2")
	void getTemplateInTrash() {
		Template trashed = TemplateFixture.template(USER_ID);
		trashed.moveToTrash(LocalDateTime.of(2026, 10, 7, 10, 30));
		given(templateRepository.findByIdAndUserId(TEMPLATE_ID, USER_ID)).willReturn(Optional.of(trashed));

		assertThatThrownBy(() -> templateQueryService.getTemplate(QUERY))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(TemplateErrorCode.TEMPLATE_IN_TRASH);
	}

	@Test
	@DisplayName("목록은 레포지토리가 읽은 순서 그대로, 개수와 본문에서 뽑은 변수 목록을 함께 돌려준다")
	void getTemplates() {
		Template first = TemplateFixture.templateWithContent(USER_ID, "{{금액}}원, {{고객명}}님 {{금액}}원");
		Template second = TemplateFixture.templateWithContent(USER_ID, "변수 없는 본문 {{잘못된 변수}}");
		given(templateRepository.findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(USER_ID))
			.willReturn(List.of(first, second));

		TemplateListResult result = templateQueryService.getTemplates(USER_ID);

		assertThat(result.totalCount()).isEqualTo(2);
		assertThat(result.templates()).extracting(TemplateListItemResult::content)
			.containsExactly("{{금액}}원, {{고객명}}님 {{금액}}원", "변수 없는 본문 {{잘못된 변수}}");
		assertThat(result.templates()).extracting(TemplateListItemResult::variables)
			.containsExactly(List.of("금액", "고객명"), List.of());
		assertThat(result.templates().getFirst().title()).isEqualTo(TemplateFixture.TITLE);
		assertThat(result.templates().getFirst().shortcut()).isEqualTo(TemplateFixture.SHORTCUT);
	}

	@Test
	@DisplayName("템플릿이 없으면 개수 0과 빈 목록이다")
	void getTemplatesEmpty() {
		given(templateRepository.findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(USER_ID)).willReturn(List.of());

		TemplateListResult result = templateQueryService.getTemplates(USER_ID);

		assertThat(result.totalCount()).isZero();
		assertThat(result.templates()).isEmpty();
	}

}
