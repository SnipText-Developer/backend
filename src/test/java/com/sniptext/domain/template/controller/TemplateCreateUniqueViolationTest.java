package com.sniptext.domain.template.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClockConfiguration;

/**
 * 저장 전 중복 조회를 통과한 뒤 실제 유니크 위반이 나는 경우(잠금 없는 수정 요청과의 경쟁)를 재현한다.
 * 중복 조회만 false로 바꾸고 같은 단축어 행을 미리 넣어 두면 실제 MySQL 유니크 위반까지 간다.
 * 클래스에 @Transactional을 붙이지 않는다. 붙이면 커밋 단계의 롤백 예외가 드러나지 않는다. 대신 만든 회원을 직접 지운다.
 */
@Import(MutableClockConfiguration.class)
class TemplateCreateUniqueViolationTest extends IntegrationTestSupport {

	private static final String SHORTCUT = "/견적";

	@MockitoSpyBean
	private TemplateRepository templateRepository;

	@Autowired
	private UserRepository userRepository;

	private Long userId;

	@BeforeEach
	void setUp() {
		userId = userRepository.save(UserFixture.user("unique@sniptext.com")).getId();
		templateRepository.save(TemplateFixture.template(userId, SHORTCUT));
		willReturn(false).given(templateRepository).existsByUserIdAndActiveShortcutKey(anyLong(), anyString());
	}

	// 회원을 지우면 외래키(ON DELETE CASCADE)로 템플릿도 지워진다
	@AfterEach
	void tearDown() {
		userRepository.deleteById(userId);
	}

	@Test
	@DisplayName("중복 조회를 통과해도 저장에서 유니크 위반이 나면 TEMPLATE409_1이고, 500·롤백 예외로 새지 않으며 행이 늘지 않는다")
	void uniqueViolationBecomesConflict() throws Exception {
		mockMvc.perform(post("/api/v1/templates")
				.header(HttpHeaders.AUTHORIZATION, bearer(userId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title": "견적 안내", "shortcut": "%s", "content": "본문"}
					""".formatted(SHORTCUT)))
			.andExpect(status().isConflict())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.code").value("TEMPLATE409_1"))
			.andExpect(jsonPath("$.result").isEmpty());

		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isEqualTo(1);
	}

}
