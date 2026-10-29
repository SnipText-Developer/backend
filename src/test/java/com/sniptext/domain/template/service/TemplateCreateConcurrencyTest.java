package com.sniptext.domain.template.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.template.service.dto.command.TemplateCreateCommand;
import com.sniptext.domain.user.entity.User;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.global.exception.BusinessException;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClockConfiguration;

/**
 * 같은 회원의 생성 요청이 동시에 와도 단축어 중복과 한도를 넘지 않는지 실제 트랜잭션으로 확인한다.
 * 클래스에 @Transactional을 붙이지 않는다. 붙이면 두 요청이 커밋되지 않아 서로를 볼 수 없다. 대신 만든 회원을 직접 지운다.
 */
@Import(MutableClockConfiguration.class)
class TemplateCreateConcurrencyTest extends IntegrationTestSupport {

	private static final String SUCCESS = "SUCCESS";
	private static final String CONTENT = "안녕하세요 {{고객명}}님";

	@Autowired
	private TemplateCommandService templateCommandService;

	@Autowired
	private TemplateRepository templateRepository;

	@Autowired
	private UserRepository userRepository;

	private Long userId;

	@BeforeEach
	void setUp() {
		userId = userRepository.save(User.builder().email("concurrency@sniptext.com").nickname("닉네임").build())
			.getId();
	}

	// 회원을 지우면 외래키(ON DELETE CASCADE)로 템플릿도 지워진다
	@AfterEach
	void tearDown() {
		userRepository.deleteById(userId);
	}

	@Test
	@DisplayName("같은 단축어로 동시에 2건이면 1건만 저장되고 1건은 TEMPLATE409_1")
	void sameShortcutAtOnce() throws Exception {
		List<String> results = runAtOnce(
			() -> create("/견적"),
			() -> create("/견적"));

		assertThat(results).containsExactlyInAnyOrder(SUCCESS, "TEMPLATE409_1");
		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isEqualTo(1);
	}

	@Test
	@DisplayName("FREE 4개 상태에서 다른 단축어로 동시에 2건이면 1건만 저장되고 1건은 TEMPLATE403_1, 최종 5개")
	void limitAtOnce() throws Exception {
		for (int i = 1; i <= 4; i++) {
			templateRepository.save(TemplateFixture.template(userId, "/t" + i));
		}

		List<String> results = runAtOnce(
			() -> create("/다섯째"),
			() -> create("/여섯째"));

		assertThat(results).containsExactlyInAnyOrder(SUCCESS, "TEMPLATE403_1");
		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isEqualTo(5);
	}

	private String create(String shortcut) {
		try {
			templateCommandService.create(new TemplateCreateCommand(userId, "견적 안내", shortcut, CONTENT));
			return SUCCESS;
		} catch (BusinessException e) {
			return e.getResultCode().getCode();
		}
	}

	/** 두 작업을 같은 순간에 출발시키고 결과를 모은다 */
	@SafeVarargs
	private static List<String> runAtOnce(Callable<String>... tasks) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.length);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<String>> futures = new ArrayList<>();
			for (Callable<String> task : tasks) {
				futures.add(executor.submit(() -> {
					start.await();
					return task.call();
				}));
			}
			start.countDown();
			List<String> results = new ArrayList<>();
			for (Future<String> future : futures) {
				results.add(future.get(10, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			executor.shutdownNow();
		}
	}

}
