package com.sniptext.domain.template.service;

import static com.sniptext.support.ConcurrentRunner.runAtOnce;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.sniptext.domain.template.entity.Shortcut;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.template.service.dto.command.TemplateCreateCommand;
import com.sniptext.domain.template.service.dto.command.TrashRestoreCommand;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.global.exception.BusinessException;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClockConfiguration;

/**
 * 같은 회원의 복구가 다른 복구·생성과 동시에 와도 한도와 단축어가 지켜지는지 실제 트랜잭션으로 확인한다.
 * 클래스에 @Transactional을 붙이지 않는다. 붙이면 두 요청이 커밋되지 않아 서로를 볼 수 없다. 대신 만든 회원을 직접 지운다.
 */
@Import(MutableClockConfiguration.class)
class TrashRestoreConcurrencyTest extends IntegrationTestSupport {

	private static final String SUCCESS = "SUCCESS";
	private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 10, 20, 10, 0);

	@Autowired
	private TrashCommandService trashCommandService;

	@Autowired
	private TemplateCommandService templateCommandService;

	@Autowired
	private TemplateRepository templateRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Long userId;

	@BeforeEach
	void setUp() {
		userId = userRepository.save(UserFixture.user("restore-concurrency@sniptext.com")).getId();
	}

	// 회원을 지우면 외래키(ON DELETE CASCADE)로 템플릿도 지워진다
	@AfterEach
	void tearDown() {
		userRepository.deleteById(userId);
	}

	@Test
	@DisplayName("49개 상태에서 복구가 동시에 2건이면 1건만 복구되고 1건은 TEMPLATE403_1, 최종 50개")
	void limitAtOnce() throws Exception {
		saveActiveCount(49);
		Long first = saveTrashed("/다섯째");
		Long second = saveTrashed("/여섯째");

		List<String> results = runAtOnce(() -> restore(first), () -> restore(second));

		assertThat(results).containsExactlyInAnyOrder(SUCCESS, "TEMPLATE403_1");
		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isEqualTo(50);
	}

	@Test
	@DisplayName("49개 상태에서 새 템플릿 저장과 복구가 동시에 오면 1건만 성공하고 1건은 TEMPLATE403_1, 최종 50개")
	void createAndRestoreLimitAtOnce() throws Exception {
		saveActiveCount(49);
		Long trashedId = saveTrashed("/다섯째");

		List<String> results = runAtOnce(() -> create("/여섯째"), () -> restore(trashedId));

		assertThat(results).containsExactlyInAnyOrder(SUCCESS, "TEMPLATE403_1");
		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isEqualTo(50);
	}

	@Test
	@DisplayName("같은 템플릿 복구가 동시에 2건이면 1건만 복구되고 1건은 TRASH404_1")
	void sameTemplateAtOnce() throws Exception {
		Long trashedId = saveTrashed("/견적");

		List<String> results = runAtOnce(() -> restore(trashedId), () -> restore(trashedId));

		assertThat(results).containsExactlyInAnyOrder(SUCCESS, "TRASH404_1");
		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isEqualTo(1);
	}

	@Test
	@DisplayName("같은 단축어를 가진 두 템플릿을 동시에 복구하면 잠금으로 줄을 서서 둘 다 복구되고 하나는 번호가 붙는다")
	void sameShortcutAtOnce() throws Exception {
		Long first = saveTrashed("/견적");
		Long second = saveTrashed("/견적");

		List<String> results = runAtOnce(() -> restore(first), () -> restore(second));

		assertThat(results).containsExactly(SUCCESS, SUCCESS);
		assertThat(templateRepository.findActiveShortcutKeys(userId, Shortcut.create("/견적").candidateKeys()))
			.containsExactlyInAnyOrder("/견적", "/견적2");
	}

	/**
	 * 정리 배치·영구 삭제는 회원을 잠그지 않으므로 복구와 줄을 서지 않는다. 지우는 쪽이 행을 먼저 잡고 있으면
	 * 복구는 그 행을 잠그며 기다렸다가, 삭제가 커밋된 뒤 행이 없다는 것을 보고 TRASH404_1이어야 한다 (저장 단계의 500이 아니라).
	 * 테스트 DB 계정은 잠금 대기를 조회할 권한이 없어서, 1초 안에 끝나지 않으면 행 잠금을 기다리는 것으로 본다.
	 */
	@Test
	@DisplayName("영구 삭제가 먼저 행을 잡고 있으면 복구는 기다렸다가 TRASH404_1")
	void purgedWhileRestoring() throws Exception {
		Long trashedId = saveTrashed("/견적");
		CountDownLatch deleted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<Void> purge = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(
			status -> {
				jdbcTemplate.update("delete from templates where id = ?", trashedId);
				deleted.countDown();
				await(release);
			}));
		await(deleted);

		CompletableFuture<String> restore = CompletableFuture.supplyAsync(() -> restore(trashedId));
		assertThatThrownBy(() -> restore.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
		release.countDown();
		purge.get(10, TimeUnit.SECONDS);

		assertThat(restore.get(10, TimeUnit.SECONDS)).isEqualTo("TRASH404_1");
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

	/** 단축어 /t1, /t2 ... 로 활성 템플릿을 미리 채운다 */
	private void saveActiveCount(int count) {
		IntStream.rangeClosed(1, count)
			.forEach(number -> templateRepository.save(TemplateFixture.template(userId, "/t" + number)));
	}

	private Long saveTrashed(String shortcut) {
		return templateRepository.save(TemplateFixture.trashedTemplate(userId, shortcut, DELETED_AT)).getId();
	}

	private String restore(Long templateId) {
		try {
			trashCommandService.restore(new TrashRestoreCommand(userId, templateId));
			return SUCCESS;
		} catch (BusinessException e) {
			return e.getResultCode().getCode();
		}
	}

	private String create(String shortcut) {
		try {
			templateCommandService.create(new TemplateCreateCommand(userId, "견적 안내", shortcut, "본문"));
			return SUCCESS;
		} catch (BusinessException e) {
			return e.getResultCode().getCode();
		}
	}

}
