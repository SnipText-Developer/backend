package com.sniptext.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.auth.entity.SocialAccount;
import com.sniptext.domain.auth.entity.SocialProvider;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.SocialAccountFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;

@Transactional
class SocialAccountRepositoryTest extends IntegrationTestSupport {

	@Autowired
	private SocialAccountRepository socialAccountRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	@DisplayName("제공자와 구글 계정 고유값으로 연결된 회원을 찾는다")
	void findByProviderAndProviderUserId() {
		Long userId = userRepository.save(UserFixture.user("social@gmail.com")).getId();
		socialAccountRepository.save(SocialAccountFixture.google(userId));

		assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE,
			SocialAccountFixture.GOOGLE_SUB))
			.map(SocialAccount::getUserId)
			.contains(userId);
		assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "unknown"))
			.isEmpty();
	}

	@Test
	@DisplayName("255자 고유값을 저장하고 찾을 수 있다")
	void maxLengthProviderUserId() {
		Long userId = userRepository.save(UserFixture.user("long@gmail.com")).getId();
		String longSub = "a".repeat(SocialAccount.PROVIDER_USER_ID_MAX_LENGTH);
		socialAccountRepository.saveAndFlush(SocialAccountFixture.google(userId, longSub));

		assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, longSub))
			.map(SocialAccount::getUserId)
			.contains(userId);
	}

	@Test
	@DisplayName("고유값은 대소문자를 구분한다. 대소문자만 다른 두 계정은 따로 연결되고 각각 찾아진다")
	void caseSensitiveProviderUserId() {
		Long lower = userRepository.save(UserFixture.user("lower@gmail.com")).getId();
		Long upper = userRepository.save(UserFixture.user("upper@gmail.com")).getId();
		socialAccountRepository.saveAndFlush(SocialAccountFixture.google(lower, "abcDEF123"));
		socialAccountRepository.saveAndFlush(SocialAccountFixture.google(upper, "ABCdef123"));

		assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "abcDEF123"))
			.map(SocialAccount::getUserId)
			.contains(lower);
		assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "ABCdef123"))
			.map(SocialAccount::getUserId)
			.contains(upper);
		assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "abcdef123"))
			.isEmpty();
	}

	@Test
	@DisplayName("같은 소셜 계정을 두 번 연결하면 DB 유니크 위반이다 (동시 가입 방지)")
	void uniqueProviderUser() {
		Long first = userRepository.save(UserFixture.user("first@gmail.com")).getId();
		Long second = userRepository.save(UserFixture.user("second@gmail.com")).getId();
		socialAccountRepository.save(SocialAccountFixture.google(first));

		assertThatThrownBy(() -> socialAccountRepository.saveAndFlush(SocialAccountFixture.google(second)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
