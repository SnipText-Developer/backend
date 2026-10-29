package com.sniptext.domain.user.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.user.entity.TermsType;
import com.sniptext.domain.user.entity.User;
import com.sniptext.domain.user.entity.UserTermsAgreement;
import com.sniptext.domain.user.exception.UserErrorCode;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.domain.user.repository.UserTermsAgreementRepository;
import com.sniptext.domain.user.service.dto.command.UserSignUpCommand;
import com.sniptext.global.exception.BusinessException;

@Service
@Transactional(readOnly = true)
public class UserCommandService {

	/** 가입할 때 동의받는 필수 약관. 마케팅처럼 선택 약관이 생기면 여기에 넣지 않는다 */
	private static final List<TermsType> REQUIRED_TERMS = List.of(TermsType.SERVICE, TermsType.PRIVACY);

	private final UserRepository userRepository;
	private final UserTermsAgreementRepository userTermsAgreementRepository;
	private final Clock clock;
	private final String currentTermsVersion;

	public UserCommandService(UserRepository userRepository,
		UserTermsAgreementRepository userTermsAgreementRepository, Clock clock,
		@Value("${sniptext.terms.current-version}") String currentTermsVersion) {
		if (currentTermsVersion.isBlank() || currentTermsVersion.length() > UserTermsAgreement.VERSION_MAX_LENGTH) {
			throw new IllegalStateException("sniptext.terms.current-version은 1~20자여야 합니다");
		}
		this.userRepository = userRepository;
		this.userTermsAgreementRepository = userTermsAgreementRepository;
		this.clock = clock;
		this.currentTermsVersion = currentTermsVersion;
	}

	/**
	 * 가입할 때 회원과 필수 약관 동의 기록(현재 버전)을 저장하고 회원 ID를 돌려준다. 가입은 바로 로그인 처리되므로 로그인 시각도 남긴다.
	 * 호출자(auth의 가입 처리) 트랜잭션에 합류해 소셜 계정·토큰 저장과 함께 커밋되거나 함께 취소된다.
	 * 혼자 커밋되면 소셜 계정 없는 회원이 남아 다시 가입할 수 없으므로, 호출자 트랜잭션 안에서만 동작하게 강제한다
	 * (밖에서 부르면 IllegalTransactionStateException).
	 * 같은 이메일이 이미 있으면 DB 유니크 위반(DataIntegrityViolationException)이 그대로 올라간다.
	 * 사용자에게 보일 에러(AUTH409_1)로 바꾸는 일은 auth가 한다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Long signUp(UserSignUpCommand command) {
		LocalDateTime now = LocalDateTime.now(clock);
		String nickname = NicknameNormalizer.normalize(command.name(), command.email());
		User user = User.create(command.email(), nickname, fitProfileImageUrl(command.profileImageUrl()));
		user.recordLogin(now);
		userRepository.save(user);

		userTermsAgreementRepository.saveAll(REQUIRED_TERMS.stream()
			.map(termsType -> UserTermsAgreement.create(user.getId(), termsType, currentTermsVersion, now))
			.toList());
		return user.getId();
	}

	/**
	 * 회원 행을 비관적 쓰기 잠금으로 잡는다. 템플릿 생성·복구·갤러리 저장의 한도 검사 전에 부른다.
	 * 자기 트랜잭션을 열면 메서드가 끝날 때 잠금이 풀려 이어지는 개수 검사·저장이 잠금 없이 실행되므로,
	 * 호출자 트랜잭션 안에서만 동작하게 강제한다 (밖에서 부르면 IllegalTransactionStateException).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void lockUser(Long userId) {
		userRepository.findByIdAndDeletedAtIsNullForUpdate(userId)
			.orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
	}

	// 컬럼보다 긴 주소 때문에 가입이 500으로 막히지 않게, 저장하지 않고 닉네임 첫 글자 아바타로 둔다
	private static String fitProfileImageUrl(String profileImageUrl) {
		if (profileImageUrl == null || profileImageUrl.isBlank()
			|| profileImageUrl.length() > User.PROFILE_IMAGE_URL_MAX_LENGTH) {
			return null;
		}
		return profileImageUrl;
	}

}
