package com.sniptext.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.user.entity.TermsType;
import com.sniptext.domain.user.entity.User;
import com.sniptext.domain.user.entity.UserTermsAgreement;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.domain.user.repository.UserTermsAgreementRepository;
import com.sniptext.domain.user.service.dto.command.UserSignUpCommand;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

/** 가입 시 회원 생성(UserCommandService.signUp). 닉네임 정리의 경계는 NicknameNormalizerTest에서 본다. */
@Transactional
@Import(MutableClockConfiguration.class)
class UserSignUpTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 21, 0);
	private static final String PROFILE = "https://lh3.googleusercontent.com/a/sample=s96-c";

	@Autowired
	private UserCommandService userCommandService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserTermsAgreementRepository userTermsAgreementRepository;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void setUp() {
		clock.setTo(NOW);
	}

	@Test
	@DisplayName("가입하면 회원 1명과 필수 약관 동의 2줄(SERVICE·PRIVACY, 현재 버전 v1, 지금 시각)이 저장된다")
	void signUp() {
		Long userId = userCommandService.signUp(new UserSignUpCommand("signup@gmail.com", "  홍길동 ", PROFILE));

		User user = userRepository.findById(userId).orElseThrow();
		assertThat(user.getEmail()).isEqualTo("signup@gmail.com");
		assertThat(user.getNickname()).isEqualTo("홍길동");
		assertThat(user.getProfileImageUrl()).isEqualTo(PROFILE);
		assertThat(userTermsAgreementRepository.findAllByUserId(userId))
			.extracting(UserTermsAgreement::getTermsType, UserTermsAgreement::getVersion, UserTermsAgreement::getAgreedAt)
			.containsExactlyInAnyOrder(
				tuple(TermsType.SERVICE, "v1", NOW),
				tuple(TermsType.PRIVACY, "v1", NOW));
	}

	@Test
	@DisplayName("구글 이름이 없으면 이메일 앞부분을 닉네임으로 저장한다")
	void nicknameFromEmail() {
		Long userId = userCommandService.signUp(new UserSignUpCommand("noname@gmail.com", null, null));

		assertThat(userRepository.findById(userId).orElseThrow().getNickname()).isEqualTo("noname");
	}

	@Test
	@DisplayName("프로필 이미지 주소가 없거나 500자를 넘으면 저장하지 않고 가입은 계속된다")
	void dropTooLongProfileImage() {
		String tooLong = "https://x.com/" + "a".repeat(User.PROFILE_IMAGE_URL_MAX_LENGTH);

		Long tooLongUserId = userCommandService.signUp(new UserSignUpCommand("long@gmail.com", "긴주소", tooLong));
		Long blankUserId = userCommandService.signUp(new UserSignUpCommand("blank@gmail.com", "빈주소", " "));

		assertThat(userRepository.findById(tooLongUserId).orElseThrow().getProfileImageUrl()).isNull();
		assertThat(userRepository.findById(blankUserId).orElseThrow().getProfileImageUrl()).isNull();
	}

	@Test
	@DisplayName("같은 이메일로 다시 가입하면 DB 유니크 위반이 그대로 올라간다 (AUTH409_1 변환은 auth의 몫)")
	void duplicateEmail() {
		userCommandService.signUp(new UserSignUpCommand("dup@gmail.com", "처음", null));

		assertThatThrownBy(() -> userCommandService.signUp(new UserSignUpCommand("dup@gmail.com", "두번째", null)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
