package com.sniptext.domain.template.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

/**
 * 사용 기록은 엔티티를 읽은 뒤 변경 감지로 쓴다. UPDATE에 last_used_at·updated_at만 들어가야
 * 읽은 뒤 다른 요청(다른 탭의 수정 등)이 커밋한 값을 옛 값으로 되돌리지 않는다 (@DynamicUpdate).
 * 조회 직후 별도 트랜잭션에서 이름을 바꿔 그 경쟁을 재현한다.
 * 클래스에 @Transactional을 붙이지 않는다. 붙이면 별도 트랜잭션의 커밋과 요청의 UPDATE가 실제로 일어나지 않는다. 대신 만든 회원을 직접 지운다.
 */
@Import(MutableClockConfiguration.class)
class TemplateUsageDynamicUpdateTest extends IntegrationTestSupport {

	private static final LocalDateTime FIXED = LocalDateTime.of(2026, 10, 11, 19, 15);
	private static final LocalDateTime LATER = FIXED.plusHours(1);
	private static final String TITLE_FROM_OTHER_TAB = "다른 탭에서 바꾼 이름";

	@MockitoSpyBean
	private TemplateRepository templateRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private MutableClock clock;

	private Long userId;

	@BeforeEach
	void setUp() {
		clock.setTo(FIXED);
		userId = userRepository.save(UserFixture.user("usage-dynamic@sniptext.com")).getId();
	}

	// 회원을 지우면 외래키(ON DELETE CASCADE)로 템플릿도 지워진다
	@AfterEach
	void tearDown() {
		userRepository.deleteById(userId);
	}

	@Test
	@DisplayName("읽은 뒤 다른 트랜잭션이 바꾼 이름을 되돌리지 않고 last_used_at·updated_at만 쓴다")
	void writesOnlyUsageColumns() throws Exception {
		Template template = templateRepository.save(TemplateFixture.template(userId));
		Long templateId = template.getId();
		renameInOtherTransactionAfterRead();
		clock.setTo(LATER);

		mockMvc.perform(post("/api/v1/templates/{templateId}/usages", templateId)
				.header(HttpHeaders.AUTHORIZATION, bearer(userId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"channel\": \"DASHBOARD_COPY\"}"))
			.andExpect(status().isOk());

		Template found = templateRepository.findById(templateId).orElseThrow();
		assertThat(found.getTitle().getValue()).isEqualTo(TITLE_FROM_OTHER_TAB);
		assertThat(found.getLastUsedAt()).isEqualTo(LATER);
		assertThat(found.getUpdatedAt()).isEqualTo(LATER);
		assertThat(found.getEditedAt()).isEqualTo(FIXED);
	}

	/**
	 * 사용 기록 요청이 템플릿을 읽은 직후, 요청 트랜잭션과 별개로 이름을 바꿔 커밋한다.
	 * 레포지토리는 인터페이스라 callRealMethod를 쓸 수 없어, 실제 빈으로 위임하는 스파이의 기본 응답으로 읽는다.
	 */
	private void renameInOtherTransactionAfterRead() {
		Answer<?> realRead = mockingDetails(templateRepository).getMockCreationSettings().getDefaultAnswer();
		TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
		requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		willAnswer(invocation -> {
			Object read = realRead.answer(invocation);
			requiresNew.executeWithoutResult(status -> jdbcTemplate.update(
				"update templates set title = ? where id = ?", TITLE_FROM_OTHER_TAB, invocation.getArgument(0)));
			return read;
		}).given(templateRepository).findByIdAndUserId(anyLong(), anyLong());
	}

}
