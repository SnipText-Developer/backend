package com.sniptext.domain.template.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClockConfiguration;

/**
 * 실제 행만으로는 닿을 수 없는 복구 에러를 조회 하나만 바꿔 재현한다. 나머지 조회와 저장은 실제 MySQL이다.
 * 클래스에 @Transactional을 붙이지 않는다. 붙이면 커밋 단계의 롤백 예외가 드러나지 않는다. 대신 만든 회원을 직접 지운다.
 */
@Import(MutableClockConfiguration.class)
class TrashRestoreConflictTest extends IntegrationTestSupport {

	private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 10, 20, 10, 0);

	@MockitoSpyBean
	private TemplateRepository templateRepository;

	@Autowired
	private UserRepository userRepository;

	private Long userId;

	@BeforeEach
	void setUp() {
		userId = userRepository.save(UserFixture.user("restore-conflict@sniptext.com")).getId();
	}

	// 회원을 지우면 외래키(ON DELETE CASCADE)로 템플릿도 지워진다
	@AfterEach
	void tearDown() {
		userRepository.deleteById(userId);
	}

	/** 원본과 2~99를 실제 행으로 채우면 활성 99개라 한도에 먼저 걸리므로, 개수 조회만 0으로 바꾼다 */
	@Test
	@DisplayName("번호 2~99가 모두 차면 TEMPLATE409_2이고 휴지통에 그대로 있다")
	void numbersExhausted() throws Exception {
		templateRepository.save(TemplateFixture.template(userId, "/견적"));
		IntStream.rangeClosed(2, 99)
			.forEach(number -> templateRepository.save(TemplateFixture.template(userId, "/견적" + number)));
		Long trashedId = saveTrashed();
		willReturn(0L).given(templateRepository).countByUserIdAndDeletedAtIsNull(userId);

		expectError(restore(trashedId), 409, "TEMPLATE409_2", "사용할 수 있는 단축어 번호가 없어요.");

		assertThat(templateRepository.findById(trashedId).orElseThrow().isInTrash()).isTrue();
	}

	/** 잠금을 잡지 않는 수정 요청이 조회와 저장 사이에 같은 단축어로 바꾼 경우다 */
	@Test
	@DisplayName("사용 중인 키 조회를 통과해도 저장에서 유니크 위반이 나면 TEMPLATE409_1이고, 500으로 새지 않으며 휴지통에 그대로 있다")
	void uniqueViolationBecomesConflict() throws Exception {
		templateRepository.save(TemplateFixture.template(userId, "/견적"));
		Long trashedId = saveTrashed();
		willReturn(Set.of()).given(templateRepository).findActiveShortcutKeys(anyLong(), anyCollection());

		expectError(restore(trashedId), 409, "TEMPLATE409_1", "이미 사용 중인 단축어예요.");

		assertThat(templateRepository.findById(trashedId).orElseThrow().isInTrash()).isTrue();
		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isEqualTo(1);
	}

	private Long saveTrashed() {
		return templateRepository.save(TemplateFixture.trashedTemplate(userId, "/견적", DELETED_AT)).getId();
	}

	private ResultActions restore(Long templateId) throws Exception {
		return mockMvc.perform(post("/api/v1/trash/templates/{templateId}/restore", templateId)
			.header(HttpHeaders.AUTHORIZATION, bearer(userId)));
	}

	private static void expectError(ResultActions actions, int status, String code, String message) throws Exception {
		actions.andExpect(status().is(status))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.message").value(message))
			.andExpect(jsonPath("$.result").isEmpty());
	}

}
