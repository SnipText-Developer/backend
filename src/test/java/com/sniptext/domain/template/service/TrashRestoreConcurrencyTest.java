package com.sniptext.domain.template.service;

import static com.sniptext.support.ConcurrentRunner.runAtOnce;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

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
