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
	@DisplayName("같은 소셜 계정을 두 번 연결하면 DB 유니크 위반이다 (동시 가입 방지)")
	void uniqueProviderUser() {
		Long first = userRepository.save(UserFixture.user("first@gmail.com")).getId();
		Long second = userRepository.save(UserFixture.user("second@gmail.com")).getId();
		socialAccountRepository.save(SocialAccountFixture.google(first));

		assertThatThrownBy(() -> socialAccountRepository.saveAndFlush(SocialAccountFixture.google(second)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
