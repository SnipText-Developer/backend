package com.sniptext.domain.template.service;

import static com.sniptext.domain.template.service.ConcurrentRunner.runAtOnce;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.template.service.dto.command.TemplateDeleteCommand;
import com.sniptext.domain.template.service.dto.command.TrashRestoreCommand;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.domain.user.service.UserCommandService;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.global.exception.BusinessException;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

/**
 * 삭제가 같은 템플릿의 다른 삭제, 같은 회원의 휴지통 비우기·복구와 겹쳐도 500 없이 끝나는지 실제 트랜잭션으로 확인한다.
 * 클래스에 @Transactional을 붙이지 않는다. 붙이면 요청들이 커밋되지 않아 서로를 볼 수 없다. 대신 만든 회원을 직접 지운다.
 * "먼저 잡고 있으면" 테스트는 앞 요청을 바깥 트랜잭션으로 감싸 커밋 직전에 멈춰 세운다.
 * 테스트 DB 계정은 잠금 대기를 조회할 권한이 없어서, 1초 안에 끝나지 않으면 잠금을 기다리는 것으로 본다.
 */
@Import(MutableClockConfiguration.class)
class TemplateDeleteConcurrencyTest extends IntegrationTestSupport {

	private static final String SUCCESS = "SUCCESS";
	private static final LocalDateTime TRASHED_AT = LocalDateTime.of(2026, 10, 20, 10, 0);
	private static final LocalDateTime FIRST = LocalDateTime.of(2026, 10, 25, 10, 0);
	private static final LocalDateTime SECOND = FIRST.plusHours(1);

	@Autowired
	private TemplateCommandService templateCommandService;

	@Autowired
	private TrashCommandService trashCommandService;

	@Autowired
	private UserCommandService userCommandService;

	@Autowired
	private TemplateRepository templateRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MutableClock clock;

	private Long userId;

	@BeforeEach
	void setUp() {
		clock.setTo(FIRST);
		userId = userRepository.save(UserFixture.user("delete-concurrency@sniptext.com")).getId();
	}

	// 회원을 지우면 외래키(ON DELETE CASCADE)로 템플릿도 지워진다
	@AfterEach
	void tearDown() {
		userRepository.deleteById(userId);
	}

	@Test
	@DisplayName("같은 템플릿 삭제가 동시에 2건이면 1건만 성공하고 1건은 TEMPLATE404_1")
	void sameTemplateAtOnce() throws Exception {
		Long templateId = saveActive("/견적");

		List<String> results = runAtOnce(() -> delete(templateId), () -> delete(templateId));

		assertThat(results).containsExactlyInAnyOrder(SUCCESS, "TEMPLATE404_1");
		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isZero();
	}

	/**
	 * 회원을 잠그지 않고 일반 조회로 읽으면, 뒤 요청은 커밋 전 스냅샷에서 활성 템플릿을 보고 UPDATE에서 행 잠금만 기다린다.
	 * 앞 요청이 커밋되면 그대로 deleted_at을 덮어써 둘 다 200이 되고 30일 기한이 다시 시작된다.
	 */
	@Test
	@DisplayName("앞 삭제가 커밋 전이면 뒤 삭제는 기다렸다가 TEMPLATE404_1이고, 휴지통 시각은 앞 삭제 시각 그대로다")
	void deleteBeforeDelete() throws Exception {
		Long templateId = saveActive("/견적");
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<String> first = holding(() -> delete(templateId), release);
		clock.setTo(SECOND);

		CompletableFuture<String> second = CompletableFuture.supplyAsync(() -> delete(templateId));
		assertWaiting(second);
		release.countDown();

		assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(SUCCESS);
		assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo("TEMPLATE404_1");
		assertThat(find(templateId).getDeletedAt()).isEqualTo(FIRST);
	}

	@Test
	@DisplayName("삭제가 커밋 전일 때 비우기가 오면 500 없이 기다렸다가, 방금 옮긴 템플릿까지 비운다")
	void deleteBeforeEmpty() throws Exception {
		Long deletingId = saveActive("/견적");
		Long trashedId = saveTrashed("/회의");
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<String> delete = holding(() -> delete(deletingId), release);

		CompletableFuture<Integer> empty = CompletableFuture.supplyAsync(this::emptyTrash);
		assertWaiting(empty);
		release.countDown();

		assertThat(delete.get(10, TimeUnit.SECONDS)).isEqualTo(SUCCESS);
		assertThat(empty.get(10, TimeUnit.SECONDS)).isEqualTo(2);
		assertThat(templateRepository.existsById(deletingId)).isFalse();
		assertThat(templateRepository.existsById(trashedId)).isFalse();
	}

	@Test
	@DisplayName("비우기가 커밋 전일 때 삭제가 오면 500 없이 기다렸다가 휴지통으로 옮기고, 비우기는 원래 휴지통만 지운다")
	void emptyBeforeDelete() throws Exception {
		Long deletingId = saveActive("/견적");
		Long trashedId = saveTrashed("/회의");
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<Integer> empty = holding(this::emptyTrash, release);

		CompletableFuture<String> delete = CompletableFuture.supplyAsync(() -> delete(deletingId));
		assertWaiting(delete);
		release.countDown();

		assertThat(empty.get(10, TimeUnit.SECONDS)).isEqualTo(1);
		assertThat(delete.get(10, TimeUnit.SECONDS)).isEqualTo(SUCCESS);
		assertThat(find(deletingId).isInTrash()).isTrue();
		assertThat(templateRepository.existsById(trashedId)).isFalse();
	}

	@Test
	@DisplayName("삭제가 커밋 전일 때 복구가 오면 500 없이 기다렸다가 둘 다 성공하고, 같은 단축어는 번호 없이 복구된다")
	void deleteBeforeRestore() throws Exception {
		Long deletingId = saveActive("/견적");
		Long restoringId = saveTrashed("/견적");
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<String> delete = holding(() -> delete(deletingId), release);

		CompletableFuture<String> restore = CompletableFuture.supplyAsync(() -> restore(restoringId));
		assertWaiting(restore);
		release.countDown();

		assertThat(delete.get(10, TimeUnit.SECONDS)).isEqualTo(SUCCESS);
		assertThat(restore.get(10, TimeUnit.SECONDS)).isEqualTo(SUCCESS);
		assertThat(find(deletingId).isInTrash()).isTrue();
		assertThat(find(restoringId).getShortcut().getValue()).isEqualTo("/견적");
	}

	@Test
	@DisplayName("복구가 커밋 전일 때 삭제가 오면 500 없이 기다렸다가 둘 다 성공한다")
	void restoreBeforeDelete() throws Exception {
		Long deletingId = saveActive("/견적");
		Long restoringId = saveTrashed("/회의");
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<String> restore = holding(() -> restore(restoringId), release);

		CompletableFuture<String> delete = CompletableFuture.supplyAsync(() -> delete(deletingId));
		assertWaiting(delete);
		release.countDown();

		assertThat(restore.get(10, TimeUnit.SECONDS)).isEqualTo(SUCCESS);
		assertThat(delete.get(10, TimeUnit.SECONDS)).isEqualTo(SUCCESS);
		assertThat(find(deletingId).isInTrash()).isTrue();
		assertThat(find(restoringId).isInTrash()).isFalse();
	}

	@Test
	@DisplayName("삭제·비우기·복구가 같은 순간에 와도 500 없이 모두 끝나고 삭제한 템플릿은 남지 않거나 휴지통에 있다")
	void deleteEmptyRestoreAtOnce() throws Exception {
		Long deletingId = saveActive("/견적");
		Long restoringId = saveTrashed("/회의");
		saveTrashed("/보고");

		List<String> results = runAtOnce(
			() -> delete(deletingId),
			() -> String.valueOf(emptyTrash()),
			() -> restore(restoringId));

		assertThat(results.get(0)).isEqualTo(SUCCESS);
		assertThat(results.get(2)).isIn(SUCCESS, "TRASH404_1");
		assertThat(templateRepository.findById(deletingId).map(Template::isInTrash).orElse(true)).isTrue();
	}

	/**
	 * 작업을 바깥 트랜잭션 안에서 실행하고, 변경을 DB에 내보낸 뒤 커밋하기 전에 release가 올 때까지 멈춰 세운다.
	 * 삭제는 변경 감지로 쓰므로 내보내지 않으면 UPDATE가 커밋 때 나가 실제 요청의 "SQL 실행 뒤·커밋 전" 틈을 흉내 내지 못한다.
	 */
	private <T> CompletableFuture<T> holding(Supplier<T> task, CountDownLatch release) {
		CountDownLatch done = new CountDownLatch(1);
		CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> transactionTemplate.execute(status -> {
			T result = task.get();
			templateRepository.flush();
			done.countDown();
			await(release);
			return result;
		}));
		await(done);
		return future;
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

	private Long saveActive(String shortcut) {
		return templateRepository.save(TemplateFixture.template(userId, shortcut)).getId();
	}

	private Long saveTrashed(String shortcut) {
		return templateRepository.save(TemplateFixture.trashedTemplate(userId, shortcut, TRASHED_AT)).getId();
	}

	private Template find(Long templateId) {
		return templateRepository.findById(templateId).orElseThrow();
	}

	private String delete(Long templateId) {
		try {
			templateCommandService.delete(new TemplateDeleteCommand(userId, templateId));
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

	/**
	 * 휴지통 비우기(18번, develop의 TrashCommandService.empty)와 같은 잠금 순서·쿼리로 지운 개수를 돌려준다.
	 * 이 브랜치의 기준(#80)에는 아직 18번이 없어 흉내 낸다. #80이 develop을 받으면 실제 메서드로 바꾼다.
	 */
	private int emptyTrash() {
		return transactionTemplate.execute(status -> {
			userCommandService.lockUser(userId);
			return jdbcTemplate.update("delete from templates where user_id = ? and deleted_at is not null", userId);
		});
	}

}
