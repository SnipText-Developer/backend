package com.sniptext.domain.template.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.template.service.dto.command.TemplateUpdateCommand;
import com.sniptext.domain.template.service.dto.command.TemplateUsageCommand;
import com.sniptext.domain.template.service.dto.query.TemplateSyncQuery;
import com.sniptext.domain.template.service.dto.result.TemplateListItemResult;
import com.sniptext.domain.template.service.dto.result.TemplateSyncResult;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

/**
 * 확장 동기화 버전(가장 늦은 updated_at)이 같은 회원의 겹친 쓰기를 놓치지 않는지 실제 트랜잭션으로 확인한다.
 * 수정·사용 기록이 회원 행을 잠그지 않으면, 시각을 먼저 받은 쓰기가 더 늦은 시각의 쓰기보다 나중에 커밋될 수 있다.
 * 그 사이 동기화하면 최댓값이 이미 뒤 시각이라, 먼저 받은 쓰기가 커밋돼도 버전이 바뀌지 않는다.
 * 클래스에 @Transactional을 붙이지 않는다. 붙이면 요청들이 커밋되지 않아 서로를 볼 수 없다. 대신 만든 회원을 직접 지운다.
 * 테스트 DB 계정은 잠금 대기를 조회할 권한이 없어서, 1초 안에 끝나지 않으면 잠금을 기다리는 것으로 본다.
 */
@Import(MutableClockConfiguration.class)
class TemplateSyncOrderConcurrencyTest extends IntegrationTestSupport {

	private static final LocalDateTime FIRST = LocalDateTime.of(2026, 11, 15, 20, 0);
	private static final LocalDateTime SECOND = FIRST.plusMinutes(1);

	@Autowired
	private TemplateCommandService templateCommandService;

	@Autowired
	private TemplateQueryService templateQueryService;

	@Autowired
	private TemplateRepository templateRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private MutableClock clock;

	private Long userId;
	private Long usedId;
	private Long editedId;

	@BeforeEach
	void setUp() {
		clock.setTo(FIRST.minusDays(1));
		userId = userRepository.save(UserFixture.user("sync-order@sniptext.com")).getId();
		usedId = templateRepository.save(TemplateFixture.template(userId, "/사용")).getId();
		editedId = templateRepository.save(TemplateFixture.template(userId, "/수정")).getId();
	}

	// 회원을 지우면 외래키(ON DELETE CASCADE)로 템플릿도 지워진다
	@AfterEach
	void tearDown() {
		userRepository.deleteById(userId);
	}

	@Test
	@DisplayName("사용 기록이 커밋 전이면 다른 템플릿 수정은 기다렸다가 커밋되고, 그 전 버전으로 동기화하면 두 변경이 모두 보인다")
	void usageBeforeUpdate() throws Exception {
		String before = version();
		clock.setTo(FIRST);
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<Void> usage = holding(() -> recordUsage(usedId), release);
		clock.setTo(SECOND);

		CompletableFuture<Void> update = CompletableFuture.runAsync(() -> update(editedId));
		assertWaiting(update);
		assertThat(version()).isEqualTo(before);
		release.countDown();
		usage.get(10, TimeUnit.SECONDS);
		update.get(10, TimeUnit.SECONDS);

		TemplateSyncResult result = templateQueryService.sync(new TemplateSyncQuery(userId, before));
		assertThat(result.changed()).isTrue();
		assertThat(result.version()).isEqualTo(TemplateSyncVersion.of(2, SECOND));
		assertThat(item(result, usedId).lastUsedAt()).isEqualTo(FIRST);
		assertThat(item(result, editedId).title()).isEqualTo("고친 이름");
	}

	@Test
	@DisplayName("수정이 커밋 전이면 다른 템플릿 사용 기록은 기다렸다가 커밋되고, 버전은 나중에 커밋된 사용 기록 시각을 담는다")
	void updateBeforeUsage() throws Exception {
		clock.setTo(FIRST);
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<Void> update = holding(() -> update(editedId), release);
		clock.setTo(SECOND);

		CompletableFuture<Void> usage = CompletableFuture.runAsync(() -> recordUsage(usedId));
		assertWaiting(usage);
		release.countDown();
		update.get(10, TimeUnit.SECONDS);
		usage.get(10, TimeUnit.SECONDS);

		assertThat(version()).isEqualTo(TemplateSyncVersion.of(2, SECOND));
	}

	/**
	 * 작업을 바깥 트랜잭션 안에서 실행하고, 변경을 DB에 내보낸 뒤 커밋하기 전에 release가 올 때까지 멈춰 세운다.
	 * 사용 기록은 변경 감지로 쓰므로 내보내야 updated_at이 그 시점 시각으로 정해진다 (실제 요청의 "SQL 실행 뒤·커밋 전" 틈).
	 */
	private CompletableFuture<Void> holding(Runnable task, CountDownLatch release) {
		CountDownLatch done = new CountDownLatch(1);
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(
			status -> {
				task.run();
				templateRepository.flush();
				done.countDown();
				await(release);
			}));
		await(done);
		return future;
	}

	private String version() {
		return templateQueryService.sync(new TemplateSyncQuery(userId, null)).version();
	}

	private void recordUsage(Long templateId) {
		templateCommandService.recordUsage(new TemplateUsageCommand(userId, templateId));
	}

	private void update(Long templateId) {
		templateCommandService.update(new TemplateUpdateCommand(userId, templateId, "고친 이름", "/수정",
			TemplateFixture.CONTENT));
	}

	private static TemplateListItemResult item(TemplateSyncResult result, Long templateId) {
		return result.templates().templates().stream()
			.filter(template -> template.templateId().equals(templateId))
			.findFirst()
			.orElseThrow();
	}

	private static void assertWaiting(CompletableFuture<?> future) {
		assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("기다리는 신호가 오지 않았다");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

}
