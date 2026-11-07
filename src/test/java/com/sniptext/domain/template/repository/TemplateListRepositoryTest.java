package com.sniptext.domain.template.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.user.entity.User;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

import jakarta.persistence.EntityManager;

/** 내 템플릿 목록 조회의 범위와 정렬을 실제 MySQL로 검증한다. */
@Transactional
@Import(MutableClockConfiguration.class)
class TemplateListRepositoryTest extends IntegrationTestSupport {

	private static final LocalDateTime FIXED = LocalDateTime.of(2026, 10, 25, 10, 0);

	@Autowired
	private TemplateRepository templateRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private MutableClock clock;

	private Long userId;
	private Long otherUserId;

	@BeforeEach
	void setUp() {
		clock.setTo(FIXED);
		userId = saveUser("owner@sniptext.com");
		otherUserId = saveUser("other@sniptext.com");
	}

	@Test
	@DisplayName("휴지통을 제외한 본인 템플릿만 ID 오름차순으로 읽는다")
	void activeTemplatesInIdOrder() {
		Long first = save(TemplateFixture.template(userId, "/첫째"));
		save(TemplateFixture.template(otherUserId, "/남의것"));
		save(TemplateFixture.trashedTemplate(userId, "/삭제", FIXED));
		Long second = save(TemplateFixture.template(userId, "/둘째"));
		Long third = save(TemplateFixture.template(userId, "/셋째"));
		entityManager.clear();

		assertThat(templateRepository.findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(userId))
			.extracting(Template::getId)
			.containsExactly(first, second, third);
	}

	@Test
	@DisplayName("템플릿이 없으면 빈 목록이다")
	void empty() {
		save(TemplateFixture.template(otherUserId));
		save(TemplateFixture.trashedTemplate(userId, "/삭제", FIXED));

		assertThat(templateRepository.findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(userId)).isEmpty();
	}

	private Long save(Template template) {
		return templateRepository.saveAndFlush(template).getId();
	}

	private Long saveUser(String email) {
		User user = UserFixture.user(email);
		entityManager.persist(user);
		entityManager.flush();
		return user.getId();
	}

}
