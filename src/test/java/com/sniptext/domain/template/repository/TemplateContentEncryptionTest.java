package com.sniptext.domain.template.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.EmbeddableValuedModelPart;
import org.hibernate.type.descriptor.converter.spi.JpaAttributeConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.user.entity.User;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.global.crypto.AesGcmContentCipher;
import com.sniptext.global.crypto.ContentCipher;
import com.sniptext.global.crypto.ContentDecryptionException;
import com.sniptext.global.crypto.EncryptedStringConverter;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/** templates.content가 실제 MySQL에 암호문으로 저장되고 JPA로는 원문으로 읽히는지 검증한다. */
@Transactional
@Import(MutableClockConfiguration.class)
class TemplateContentEncryptionTest extends IntegrationTestSupport {

	private static final LocalDateTime FIXED = LocalDateTime.of(2026, 10, 25, 10, 0, 0, 123_456_000);

	@Autowired
	private TemplateRepository templateRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private ContentCipher contentCipher;

	@Autowired
	private MutableClock clock;

	private Long userId;

	@BeforeEach
	void setUp() {
		clock.setTo(FIXED);
		userId = saveUser("crypto@sniptext.com");
	}

	@Test
	@DisplayName("DB의 content 컬럼에는 원문이 아니라 v1:로 시작하는 암호문이 들어간다")
	void storedEncrypted() {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(userId));

		String stored = storedContent(template.getId());

		assertThat(stored).startsWith("v1:").isNotEqualTo(TemplateFixture.CONTENT).doesNotContain("고객명");
	}

	@Test
	@DisplayName("JPA로 다시 읽으면 원문이다")
	void readsPlaintext() {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(userId));
		entityManager.clear();

		Template found = templateRepository.findById(template.getId()).orElseThrow();

		assertThat(found.getContent().getValue()).isEqualTo(TemplateFixture.CONTENT);
	}

	@Test
	@DisplayName("Hibernate가 쓰는 변환기에는 스프링의 ContentCipher 빈이 주입돼 있다")
	void converterGetsSpringBean() {
		EncryptedStringConverter converter = contentConverter();

		assertThat(ReflectionTestUtils.getField(converter, "cipher")).isSameAs(contentCipher);
	}

	@Test
	@DisplayName("DB 암호문은 스프링의 ContentCipher 빈으로 복호화된다")
	void storedValueDecryptsWithBean() {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(userId));

		assertThat(contentCipher.decrypt(storedContent(template.getId()))).isEqualTo(TemplateFixture.CONTENT);
	}

	@Test
	@DisplayName("읽기만 하고 다시 flush하면 UPDATE가 나가지 않아 암호문과 updated_at이 그대로다")
	void noUpdateWithoutChange() {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(userId));
		String storedBefore = storedContent(template.getId());
		entityManager.clear();
		clock.advance(Duration.ofHours(1));

		Template found = templateRepository.findById(template.getId()).orElseThrow();
		templateRepository.saveAndFlush(found);
		entityManager.clear();

		// 암호화는 매번 IV가 달라서, UPDATE가 나갔다면 암호문이 바뀐다
		assertThat(storedContent(template.getId())).isEqualTo(storedBefore);
		assertThat(templateRepository.findById(template.getId()).orElseThrow().getUpdatedAt()).isEqualTo(FIXED);
	}

	@Test
	@DisplayName("다른 키로 암호화된 본문을 읽으면 원인 사슬에 복호화 실패 예외가 있고, 메시지에 저장값이 없다")
	void otherKeyRowFailsWithDecryptionException() {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(userId));
		String otherKeyValue = new AesGcmContentCipher(new byte[32]).encrypt(TemplateFixture.CONTENT);
		entityManager.createNativeQuery("update templates set content = :content where id = :id")
			.setParameter("content", otherKeyValue)
			.setParameter("id", template.getId())
			.executeUpdate();
		entityManager.clear();

		Throwable thrown = catchThrowable(() -> templateRepository.findById(template.getId()));

		assertThat(causeChain(thrown)).hasAtLeastOneElementOfType(ContentDecryptionException.class);
		assertThat(causeChain(thrown)).allSatisfy(t ->
			assertThat(String.valueOf(t.getMessage())).doesNotContain(otherKeyValue, TemplateFixture.CONTENT));
	}

	private String storedContent(Long templateId) {
		return (String)entityManager.createNativeQuery("select content from templates where id = :id")
			.setParameter("id", templateId)
			.getSingleResult();
	}

	private static List<Throwable> causeChain(Throwable thrown) {
		List<Throwable> chain = new ArrayList<>();
		for (Throwable t = thrown; t != null; t = t.getCause()) {
			chain.add(t);
		}
		return chain;
	}

	/** Hibernate 매핑 정보에서 Template.content.value에 붙은 변환기 인스턴스를 꺼낸다 */
	private EncryptedStringConverter contentConverter() {
		SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
		EmbeddableValuedModelPart content = (EmbeddableValuedModelPart)sessionFactory.getMappingMetamodel()
			.getEntityDescriptor(Template.class)
			.findAttributeMapping("content");
		AttributeMapping value = content.getEmbeddableTypeDescriptor().findAttributeMapping("value");
		JpaAttributeConverter<?, ?> converter =
			(JpaAttributeConverter<?, ?>)value.getSingleJdbcMapping().getValueConverter();
		assertThat(converter).as("content 컬럼에 JPA 변환기가 붙어 있어야 한다").isNotNull();
		return (EncryptedStringConverter)converter.getConverterBean().getBeanInstance();
	}

	private Long saveUser(String email) {
		User user = UserFixture.user(email);
		entityManager.persist(user);
		entityManager.flush();
		return user.getId();
	}

}
