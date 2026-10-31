package com.sniptext.domain.template.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClockConfiguration;

/**
 * 수정은 회원 잠금을 잡지 않으므로 중복 조회와 저장 사이에 다른 요청이 같은 단축어를 차지할 수 있다.
 * 중복 조회만 false로 바꾸면 실제 MySQL 유니크 위반까지 가서 이 경쟁을 재현할 수 있다.
 * 클래스에 @Transactional을 붙이지 않는다. 붙이면 커밋 단계의 롤백 예외가 드러나지 않는다. 대신 만든 회원을 직접 지운다.
 */
@Import(MutableClockConfiguration.class)
class TemplateUpdateUniqueViolationTest extends IntegrationTestSupport {

	private static final String TAKEN_SHORTCUT = "/견적";

	@MockitoSpyBean
	private TemplateRepository templateRepository;

	@Autowired
	private UserRepository userRepository;

	private Long userId;

	@BeforeEach
	void setUp() {
		userId = userRepository.save(UserFixture.user("update-unique@sniptext.com")).getId();
		templateRepository.save(TemplateFixture.template(userId, TAKEN_SHORTCUT));
		willReturn(false).given(templateRepository)
			.existsByUserIdAndActiveShortcutKeyAndIdNot(anyLong(), anyString(), anyLong());
	}

	// 회원을 지우면 외래키(ON DELETE CASCADE)로 템플릿도 지워진다
	@AfterEach
	void tearDown() {
		userRepository.deleteById(userId);
	}

	@Test
	@DisplayName("중복 조회를 통과해도 저장에서 유니크 위반이 나면 TEMPLATE409_1이고, 500·롤백 예외로 새지 않으며 수정이 남지 않는다")
	void uniqueViolationBecomesConflict() throws Exception {
		Template template = templateRepository.save(TemplateFixture.template(userId, "/내단축어"));

		mockMvc.perform(put("/api/v1/templates/{templateId}", template.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(userId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title": "새 이름", "shortcut": "%s", "content": "새 본문"}
					""".formatted(TAKEN_SHORTCUT)))
			.andExpect(status().isConflict())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.code").value("TEMPLATE409_1"))
			.andExpect(jsonPath("$.result").isEmpty());

		Template found = templateRepository.findById(template.getId()).orElseThrow();
		assertThat(found.getTitle().getValue()).isEqualTo(TemplateFixture.TITLE);
		assertThat(found.getShortcut().getValue()).isEqualTo("/내단축어");
	}

}
