package com.sniptext.domain.template.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sniptext.domain.template.entity.ShortcutUnavailableReason;
import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.exception.TemplateErrorCode;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.template.service.dto.query.TemplateDetailQuery;
import com.sniptext.domain.template.service.dto.query.TemplateShortcutAvailabilityQuery;
import com.sniptext.domain.template.service.dto.query.TemplateSyncQuery;
import com.sniptext.domain.template.service.dto.result.TemplateDetailResult;
import com.sniptext.domain.template.service.dto.result.TemplateListItemResult;
import com.sniptext.domain.template.service.dto.result.TemplateListResult;
import com.sniptext.domain.template.service.dto.result.TemplateShortcutAvailabilityResult;
import com.sniptext.domain.template.service.dto.result.TemplateSyncResult;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.global.config.TimeConfig;
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

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 20, 31, 10);
	private static final LocalDateTime LATEST_UPDATED_AT = LocalDateTime.of(2024, 11, 15, 20, 11, 1);
	private static final String CURRENT_VERSION = "v1.1.1731669061000000";

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

	@Test
	@DisplayName("단축어 확인: 활성 템플릿에 같은 비교 키가 없으면 사용 가능이고 사유는 없다")
	void shortcutAvailable() {
		given(templateRepository.existsByUserIdAndActiveShortcutKey(USER_ID, "/견적")).willReturn(false);

		TemplateShortcutAvailabilityResult result = checkShortcut("/견적", null);

		assertThat(result.shortcut()).isEqualTo("/견적");
		assertThat(result.available()).isTrue();
		assertThat(result.reason()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {"/a", "/견적요청서작성안내문서", "견적", "/견 적", "   ", " /견적", "/견적!"})
	@DisplayName("단축어 확인: 형식이 틀리면 INVALID_FORMAT이고 중복은 조회하지 않는다")
	void shortcutInvalidFormat(String shortcut) {
		TemplateShortcutAvailabilityResult result = checkShortcut(shortcut, null);

		assertUnavailable(result, shortcut, ShortcutUnavailableReason.INVALID_FORMAT);
		verifyNoInteractions(templateRepository);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "/"})
	@DisplayName("단축어 확인: 비었거나 \"/\"만 있어도 에러가 아니라 INVALID_FORMAT이다")
	void shortcutEmpty(String shortcut) {
		TemplateShortcutAvailabilityResult result = checkShortcut(shortcut, null);

		assertUnavailable(result, shortcut, ShortcutUnavailableReason.INVALID_FORMAT);
		verifyNoInteractions(templateRepository);
	}

	@Test
	@DisplayName("단축어 확인: 예약어 /톤교정은 RESERVED이고 중복은 조회하지 않는다")
	void shortcutReserved() {
		TemplateShortcutAvailabilityResult result = checkShortcut("/톤교정", null);

		assertUnavailable(result, "/톤교정", ShortcutUnavailableReason.RESERVED);
		verifyNoInteractions(templateRepository);
	}

	@Test
	@DisplayName("단축어 확인: 활성 템플릿에 같은 비교 키가 있으면 DUPLICATED")
	void shortcutDuplicated() {
		given(templateRepository.existsByUserIdAndActiveShortcutKey(USER_ID, "/견적")).willReturn(true);

		assertUnavailable(checkShortcut("/견적", null), "/견적", ShortcutUnavailableReason.DUPLICATED);
	}

	@Test
	@DisplayName("단축어 확인: 대소문자만 다른 단축어는 소문자 비교 키로 찾아 DUPLICATED, 응답 단축어는 입력 대소문자 그대로다")
	void shortcutDuplicatedIgnoringCase() {
		given(templateRepository.existsByUserIdAndActiveShortcutKey(USER_ID, "/quote")).willReturn(true);

		assertUnavailable(checkShortcut("/QUOTE", null), "/QUOTE", ShortcutUnavailableReason.DUPLICATED);
	}

	@Test
	@DisplayName("단축어 확인: excludeTemplateId가 있으면 그 템플릿을 빼고 중복을 판정한다")
	void shortcutExcludesSelf() {
		given(templateRepository.existsByUserIdAndActiveShortcutKeyAndIdNot(USER_ID, "/견적", TEMPLATE_ID))
			.willReturn(false);

		TemplateShortcutAvailabilityResult result = checkShortcut("/견적", TEMPLATE_ID);

		assertThat(result.available()).isTrue();
		assertThat(result.reason()).isNull();
	}

	@Test
	@DisplayName("단축어 확인: 자기 자신을 빼도 다른 활성 템플릿이 같은 키를 쓰면 DUPLICATED")
	void shortcutDuplicatedExcludingSelf() {
		given(templateRepository.existsByUserIdAndActiveShortcutKeyAndIdNot(USER_ID, "/견적", TEMPLATE_ID))
			.willReturn(true);

		assertUnavailable(checkShortcut("/견적", TEMPLATE_ID), "/견적", ShortcutUnavailableReason.DUPLICATED);
	}

	@Test
	@DisplayName("단축어 확인: 자모 분리형은 완성형으로 정규화해 판정하고, 응답 단축어도 정규화한 값이다")
	void shortcutNormalized() {
		String nfd = Normalizer.normalize("/견적", Normalizer.Form.NFD);
		given(templateRepository.existsByUserIdAndActiveShortcutKey(USER_ID, "/견적")).willReturn(true);

		assertUnavailable(checkShortcut(nfd, null), "/견적", ShortcutUnavailableReason.DUPLICATED);
	}

	@Test
	@DisplayName("단축어 확인: 형식이 틀린 자모 분리형 입력도 응답 단축어는 정규화한 값이다")
	void shortcutInvalidFormatNormalized() {
		String nfd = Normalizer.normalize("/견 적", Normalizer.Form.NFD);

		assertUnavailable(checkShortcut(nfd, null), "/견 적", ShortcutUnavailableReason.INVALID_FORMAT);
	}

	private TemplateShortcutAvailabilityResult checkShortcut(String shortcut, Long excludeTemplateId) {
		return templateQueryService.checkShortcutAvailability(
			new TemplateShortcutAvailabilityQuery(USER_ID, shortcut, excludeTemplateId));
	}

	private static void assertUnavailable(TemplateShortcutAvailabilityResult result, String shortcut,
		ShortcutUnavailableReason reason) {
		assertThat(result.shortcut()).isEqualTo(shortcut);
		assertThat(result.available()).isFalse();
		assertThat(result.reason()).isEqualTo(reason);
	}

	@Test
	@DisplayName("동기화: 보낸 버전이 지금 버전과 같으면 목록을 읽지 않고 changed = false, 목록 null, 시각은 Clock 기준")
	void syncUnchanged() {
		givenCurrentVersion();

		TemplateSyncResult result = syncService().sync(new TemplateSyncQuery(USER_ID, CURRENT_VERSION));

		assertThat(result.changed()).isFalse();
		assertThat(result.version()).isEqualTo(CURRENT_VERSION);
		assertThat(result.syncedAt()).isEqualTo(NOW);
		assertThat(result.templates()).isNull();
		verify(templateRepository, never()).findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(anyLong());
	}

	@ParameterizedTest(name = "\"{0}\"")
	@ValueSource(strings = {"", "v1.1.1731669061000001", "v1.2.1731669061000000", "잘못된 값"})
	@DisplayName("동기화: 보낸 버전이 지금 버전과 다르면 changed = true와 8번 목록 전체")
	void syncChanged(String version) {
		givenCurrentVersion();
		given(templateRepository.findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(USER_ID))
			.willReturn(List.of(TemplateFixture.template(USER_ID)));

		TemplateSyncResult result = syncService().sync(new TemplateSyncQuery(USER_ID, version));

		assertThat(result.changed()).isTrue();
		assertThat(result.version()).isEqualTo(CURRENT_VERSION);
		assertThat(result.templates().totalCount()).isEqualTo(1);
		assertThat(result.templates().templates()).extracting(TemplateListItemResult::shortcut)
			.containsExactly(TemplateFixture.SHORTCUT);
	}

	@Test
	@DisplayName("동기화: 버전 없이 부르면(처음 요청) 항상 changed = true")
	void syncWithoutVersion() {
		givenCurrentVersion();
		given(templateRepository.findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(USER_ID)).willReturn(List.of());

		TemplateSyncResult result = syncService().sync(new TemplateSyncQuery(USER_ID, null));

		assertThat(result.changed()).isTrue();
		assertThat(result.templates()).isNotNull();
	}

	@Test
	@DisplayName("동기화: 템플릿이 하나도 없으면 버전은 v1.0.0")
	void syncEmpty() {
		given(templateRepository.countByUserIdAndDeletedAtIsNull(USER_ID)).willReturn(0L);
		given(templateRepository.findLatestUpdatedAt(USER_ID)).willReturn(Optional.empty());

		TemplateSyncResult result = syncService().sync(new TemplateSyncQuery(USER_ID, "v1.0.0"));

		assertThat(result.changed()).isFalse();
		assertThat(result.version()).isEqualTo("v1.0.0");
	}

	private void givenCurrentVersion() {
		given(templateRepository.countByUserIdAndDeletedAtIsNull(USER_ID)).willReturn(1L);
		given(templateRepository.findLatestUpdatedAt(USER_ID)).willReturn(Optional.of(LATEST_UPDATED_AT));
	}

	/** 동기화는 현재 시각이 필요해 고정 Clock을 넣어 직접 만든다 */
	private TemplateQueryService syncService() {
		Clock clock = Clock.fixed(NOW.atZone(TimeConfig.KST).toInstant(), TimeConfig.KST);
		return new TemplateQueryService(templateRepository, clock);
	}

}
