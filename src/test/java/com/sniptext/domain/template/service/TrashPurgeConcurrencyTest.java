package com.sniptext.domain.template.service;

import static com.sniptext.domain.template.service.ConcurrentRunner.runAtOnce;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willAnswer;

import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.template.service.dto.command.TrashPurgeCommand;
import com.sniptext.domain.template.service.dto.command.TrashRestoreCommand;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.global.exception.BusinessException;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClockConfiguration;

/**
 * 영구 삭제·비우기가 같은 템플릿의 다른 영구 삭제·복구와 겹쳐도 500 없이 한쪽만 성공하는지 실제 트랜잭션으로 확인한다.
 * 클래스에 @Transactional을 붙이지 않는다. 붙이면 요청들이 커밋되지 않아 서로를 볼 수 없다. 대신 만든 회원을 직접 지운다.
 * 테스트 DB 계정은 잠금 대기를 조회할 권한이 없어서, 1초 안에 끝나지 않으면 잠금을 기다리는 것으로 본다.
 */
@Import(MutableClockConfiguration.class)
class TrashPurgeConcurrencyTest extends IntegrationTestSupport {

	private static final String SUCCESS = "SUCCESS";
	private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 10, 20, 10, 0);

	@Autowired
	private TrashCommandService trashCommandService;

	/** 복구를 템플릿 잠금 조회 뒤·저장 전에 멈춰 세우는 데만 쓴다. 나머지는 실제 동작이다 */
	@MockitoSpyBean
	private TemplateCommandService templateCommandService;

	@Autowired
	private TemplateRepository templateRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private Long userId;

	@BeforeEach
	void setUp() {
		userId = userRepository.save(UserFixture.user("purge-concurrency@sniptext.com")).getId();
	}

	// 회원을 지우면 외래키(ON DELETE CASCADE)로 템플릿도 지워진다
	@AfterEach
	void tearDown() {
		userRepository.deleteById(userId);
	}

	@Test
	@DisplayName("같은 템플릿 영구 삭제가 동시에 2건이면 1건만 성공하고 1건은 TRASH404_1")
	void samePurgeAtOnce() throws Exception {
		Long trashedId = saveTrashed("/견적");

		List<String> results = runAtOnce(() -> purge(trashedId), () -> purge(trashedId));

		assertThat(results).containsExactlyInAnyOrder(SUCCESS, "TRASH404_1");
		assertThat(templateRepository.existsById(trashedId)).isFalse();
	}

	@Test
	@DisplayName("같은 템플릿 복구와 영구 삭제가 동시에 오면 1건만 성공하고 1건은 TRASH404_1")
	void restoreAndPurgeAtOnce() throws Exception {
		Long trashedId = saveTrashed("/견적");

		List<String> results = runAtOnce(() -> restore(trashedId), () -> purge(trashedId));

		assertThat(results).containsOnlyOnce(SUCCESS).contains("TRASH404_1");
	}

	@Test
	@DisplayName("영구 삭제가 먼저 행을 지우고 있으면 복구는 기다렸다가 TRASH404_1이고 행은 지워진다")
	void purgeBeforeRestore() throws Exception {
		Long trashedId = saveTrashed("/견적");
		CountDownLatch deleted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<String> purge = CompletableFuture.supplyAsync(() -> transactionTemplate.execute(status -> {
			String result = purge(trashedId);
			deleted.countDown();
			await(release);
			return result;
		}));
		await(deleted);

		CompletableFuture<String> restore = CompletableFuture.supplyAsync(() -> restore(trashedId));
		assertThatThrownBy(() -> restore.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
		release.countDown();

		assertThat(purge.get(10, TimeUnit.SECONDS)).isEqualTo(SUCCESS);
		assertThat(restore.get(10, TimeUnit.SECONDS)).isEqualTo("TRASH404_1");
		assertThat(templateRepository.existsById(trashedId)).isFalse();
	}

	@Test
	@DisplayName("복구가 먼저 행을 잡고 있으면 영구 삭제는 기다렸다가 TRASH404_1이고 템플릿은 복구된다")
	void restoreBeforePurge() throws Exception {
		Long trashedId = saveTrashed("/견적");
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<String> restore = restoreHoldingLock(trashedId, release);

		CompletableFuture<String> purge = CompletableFuture.supplyAsync(() -> purge(trashedId));
		assertThatThrownBy(() -> purge.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
		release.countDown();

		assertThat(restore.get(10, TimeUnit.SECONDS)).isEqualTo(SUCCESS);
		assertThat(purge.get(10, TimeUnit.SECONDS)).isEqualTo("TRASH404_1");
		assertThat(templateRepository.findById(trashedId).orElseThrow().isInTrash()).isFalse();
	}

	@Test
	@DisplayName("복구가 먼저 행을 잡고 있을 때 비우기가 오면 500 없이 둘 다 끝나고, 복구한 템플릿은 남고 나머지 휴지통은 비워진다")
	void restoreBeforeEmpty() throws Exception {
		Long restoringId = saveTrashed("/견적");
		Long otherTrashedId = saveTrashed("/회의");
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<String> restore = restoreHoldingLock(restoringId, release);

		CompletableFuture<String> empty = CompletableFuture.supplyAsync(this::empty);
		assertThatThrownBy(() -> empty.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
		release.countDown();

		assertThat(restore.get(10, TimeUnit.SECONDS)).isEqualTo(SUCCESS);
		assertThat(empty.get(10, TimeUnit.SECONDS)).isEqualTo("1");
		assertThat(templateRepository.findById(restoringId).orElseThrow().isInTrash()).isFalse();
		assertThat(templateRepository.existsById(otherTrashedId)).isFalse();
	}

	/** 복구를 출발시켜 템플릿 행을 잠근 뒤(단축어 고르기 직전) release가 올 때까지 멈춰 세운다 */
	private CompletableFuture<String> restoreHoldingLock(Long templateId, CountDownLatch release) {
		CountDownLatch locked = new CountDownLatch(1);
		willAnswer(invocation -> {
			locked.countDown();
			await(release);
			return invocation.callRealMethod();
		}).given(templateCommandService).assignShortcut(eq(userId), any());
		CompletableFuture<String> restore = CompletableFuture.supplyAsync(() -> restore(templateId));
		await(locked);
		return restore;
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

	private Long saveTrashed(String shortcut) {
		return templateRepository.save(TemplateFixture.trashedTemplate(userId, shortcut, DELETED_AT)).getId();
	}

	private String purge(Long templateId) {
		try {
			trashCommandService.purge(new TrashPurgeCommand(userId, templateId));
			return SUCCESS;
		} catch (BusinessException e) {
			return e.getResultCode().getCode();
		}
	}

	private String restore(Long templateId) {
		try {
			trashCommandService.restore(new TrashRestoreCommand(userId, templateId));
			return SUCCESS;
		} catch (BusinessException e) {
			return e.getResultCode().getCode();
		}
	}

	/** 지운 개수를 문자열로 돌려준다 */
	private String empty() {
		return String.valueOf(trashCommandService.empty(userId).deletedCount());
	}

}
