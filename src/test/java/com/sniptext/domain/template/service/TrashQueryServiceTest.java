package com.sniptext.domain.template.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.exception.TrashErrorCode;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.template.service.dto.query.TrashListQuery;
import com.sniptext.domain.template.service.dto.result.TrashListResult;
import com.sniptext.domain.template.service.dto.result.TrashTemplateResult;
import com.sniptext.domain.template.support.TrashCursor;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.global.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class TrashQueryServiceTest {

	private static final Long USER_ID = 1L;
	private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 10, 7, 10, 58, 0, 123_456_000);

	@Mock
	private TemplateRepository templateRepository;

	@InjectMocks
	private TrashQueryService trashQueryService;

	@Test
	@DisplayName("커서가 없으면 첫 페이지를 다음 항목 확인용 1개까지 더 읽는다")
	void firstPage() {
		given(templateRepository.findTrashFirstPage(USER_ID, Limit.of(3))).willReturn(List.of(trashed(9L, "/가나")));

		TrashListResult result = trashQueryService.getTrash(new TrashListQuery(USER_ID, null, 2));

		assertThat(result.templates()).extracting(TrashTemplateResult::templateId).containsExactly(9L);
		verify(templateRepository, never()).findTrashAfter(any(), any(), any(), any());
	}

	@Test
	@DisplayName("빈 커서는 첫 요청으로 본다")
	void emptyCursor() {
		given(templateRepository.findTrashFirstPage(USER_ID, Limit.of(3))).willReturn(List.of());

		TrashListResult result = trashQueryService.getTrash(new TrashListQuery(USER_ID, "", 2));

		assertThat(result.templates()).isEmpty();
	}

	@Test
	@DisplayName("커서가 있으면 커서에 담긴 삭제 시각과 ID 다음부터 읽는다")
	void nextPage() {
		String cursor = new TrashCursor(DELETED_AT, 18L).encode();
		given(templateRepository.findTrashAfter(USER_ID, DELETED_AT, 18L, Limit.of(3)))
			.willReturn(List.of(trashed(9L, "/가나")));

		TrashListResult result = trashQueryService.getTrash(new TrashListQuery(USER_ID, cursor, 2));

		assertThat(result.templates()).extracting(TrashTemplateResult::templateId).containsExactly(9L);
		verify(templateRepository, never()).findTrashFirstPage(any(), any());
	}

	@Test
	@DisplayName("더 읽은 항목이 있으면 잘라내고, 커서는 이 페이지 마지막 항목을 가리킨다")
	void hasNext() {
		given(templateRepository.findTrashFirstPage(USER_ID, Limit.of(3)))
			.willReturn(List.of(trashed(9L, "/가나"), trashed(8L, "/다라"), trashed(7L, "/마바")));

		TrashListResult result = trashQueryService.getTrash(new TrashListQuery(USER_ID, null, 2));

		assertThat(result.templates()).extracting(TrashTemplateResult::templateId).containsExactly(9L, 8L);
		assertThat(result.hasNext()).isTrue();
		assertThat(result.nextCursor()).isEqualTo(new TrashCursor(DELETED_AT, 8L).encode());
	}

	@Test
	@DisplayName("요청한 개수만큼만 있으면 마지막 페이지다")
	void lastPage() {
		given(templateRepository.findTrashFirstPage(USER_ID, Limit.of(3)))
			.willReturn(List.of(trashed(9L, "/가나"), trashed(8L, "/다라")));

		TrashListResult result = trashQueryService.getTrash(new TrashListQuery(USER_ID, null, 2));

		assertThat(result.hasNext()).isFalse();
		assertThat(result.nextCursor()).isNull();
	}

	@Test
	@DisplayName("전체 개수는 페이지 크기와 상관없이 따로 센 값이다")
	void totalCount() {
		given(templateRepository.findTrashFirstPage(USER_ID, Limit.of(2))).willReturn(List.of(trashed(9L, "/가나")));
		given(templateRepository.countByUserIdAndDeletedAtIsNotNull(USER_ID)).willReturn(7L);

		TrashListResult result = trashQueryService.getTrash(new TrashListQuery(USER_ID, null, 1));

		assertThat(result.totalCount()).isEqualTo(7L);
	}

	@Test
	@DisplayName("자동 영구 삭제 예정 시각은 삭제 시각의 30일 뒤다")
	void purgeAt() {
		given(templateRepository.findTrashFirstPage(USER_ID, Limit.of(2))).willReturn(List.of(trashed(9L, "/가나")));

		TrashListResult result = trashQueryService.getTrash(new TrashListQuery(USER_ID, null, 1));

		assertThat(result.templates().getFirst().purgeAt()).isEqualTo(DELETED_AT.plusDays(30));
	}

	@Test
	@DisplayName("해석할 수 없는 커서는 TRASH400_1이고 DB를 읽지 않는다")
	void invalidCursor() {
		assertThatThrownBy(() -> trashQueryService.getTrash(new TrashListQuery(USER_ID, "!!!", 2)))
			.isInstanceOf(BusinessException.class)
			.extracting(exception -> ((BusinessException)exception).getResultCode())
			.isEqualTo(TrashErrorCode.INVALID_PAGE_REQUEST);

		verify(templateRepository, never()).findTrashFirstPage(any(), any());
		verify(templateRepository, never()).findTrashAfter(any(), any(), any(), any());
	}

	private static Template trashed(Long id, String shortcut) {
		Template template = TemplateFixture.trashedTemplate(USER_ID, shortcut, DELETED_AT);
		ReflectionTestUtils.setField(template, "id", id);
		return template;
	}

}
