package com.sniptext.domain.template.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sniptext.domain.template.entity.Shortcut;
import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.entity.TemplateContent;
import com.sniptext.domain.template.entity.TemplateTitle;
import com.sniptext.domain.template.exception.TemplateErrorCode;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.template.service.dto.command.TemplateCreateCommand;
import com.sniptext.domain.template.service.dto.command.TemplateCreateFromGalleryCommand;
import com.sniptext.domain.template.service.dto.command.TemplateUpdateCommand;
import com.sniptext.domain.template.service.dto.command.TemplateUsageCommand;
import com.sniptext.domain.template.service.dto.result.TemplateCreateResult;
import com.sniptext.domain.template.service.dto.result.TemplateShortcutAssignResult;
import com.sniptext.domain.template.service.dto.result.TemplateUpdateResult;
import com.sniptext.domain.template.service.dto.result.TemplateUsageResult;
import com.sniptext.domain.user.service.UserCommandService;
import com.sniptext.global.exception.BusinessException;
import com.sniptext.global.policy.UsageLimits;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TemplateCommandService {

	private static final String ACTIVE_SHORTCUT_UNIQUE = "uk_templates_user_active_shortcut";

	private final TemplateRepository templateRepository;

	private final UserCommandService userCommandService;

	private final TemplateQueryService templateQueryService;

	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	/**
	 * 새 템플릿 저장. 회원 행을 먼저 잠가 같은 회원의 생성·복구·갤러리 저장을 한 줄로 세운 뒤 한도를 검사한다.
	 * 두 탭에서 동시에 저장해도 한도를 넘지 않게 하기 위해서다.
	 * 한도 도달이면 TEMPLATE403_1, 활성 단축어가 겹치면 TEMPLATE409_1 (둘 다 해당하면 한도가 먼저다).
	 */
	@Transactional
	public TemplateCreateResult create(TemplateCreateCommand command) {
		userCommandService.lockUser(command.userId());

		validateTemplateLimit(command.userId());

		TemplateTitle title = TemplateTitle.create(command.title());
		Shortcut shortcut = Shortcut.create(command.shortcut());
		validateShortcutAvailable(command.userId(), shortcut);
		TemplateContent content = TemplateContent.create(command.content());

		Template template = Template.create(command.userId(), title, shortcut, content);

		return TemplateCreateResult.from(save(template));
	}

	/**
	 * 갤러리 템플릿을 내 템플릿으로 저장. 잠금·한도 순서는 create와 같고, 단축어가 겹치면 에러 대신 자동 번호를 붙인다.
	 * 한도 도달이면 TEMPLATE403_1, 번호 2~99가 모두 차면 TEMPLATE409_2 (둘 다 해당하면 한도가 먼저다).
	 * 호출자 트랜잭션 안에서 부르면 예외를 던지고, 트랜잭션은 @Transactional 대신 TransactionTemplate으로 직접 연다.
	 * MySQL 기본 격리 수준(REPEATABLE READ)은 첫 일반 조회 시점의 스냅샷을 끝까지 쓴다. 호출자가 갤러리 원본을 같은 트랜잭션에서 먼저 읽으면
	 * 잠금을 기다리는 사이 커밋된 행을 개수 검사가 보지 못해 한도를 넘기 때문이다. 첫 조회가 회원 잠금이어야 한다.
	 * NEVER는 바깥 트랜잭션이 없을 때 트랜잭션 없이 실행해 lockUser(MANDATORY)가 실패하므로 쓸 수 없다.
	 */
	public TemplateShortcutAssignResult createFromGallery(TemplateCreateFromGalleryCommand command) {
		requireNoOuterTransaction();

		return transactionTemplate.execute(status -> {
			userCommandService.lockUser(command.userId());

			validateTemplateLimit(command.userId());

			Shortcut original = Shortcut.create(command.shortcut());
			Shortcut assigned = assignShortcut(command.userId(), original);

			Template template = Template.createFromGallery(
				command.userId(),
				command.galleryTemplateId(),
				TemplateTitle.create(command.title()),
				assigned,
				TemplateContent.create(command.content()));

			return TemplateShortcutAssignResult.of(save(template), original);
		});
	}

	/**
	 * 템플릿 수정. 없거나 다른 회원 것이면 TEMPLATE404_1, 휴지통에 있으면 TEMPLATE404_2, 내 다른 활성 템플릿과 단축어가 겹치면 TEMPLATE409_1.
	 * 개수가 바뀌지 않으므로 회원을 잠그지 않고, 동시에 고치면 나중 저장이 덮어쓴다.
	 * 조회 뒤 저장 전에 휴지통으로 옮겨지면 TEMPLATE404_2 대신 200이고 수정은 휴지통 행에 남는다.
	 * 휴지통은 단축어를 점유하지 않아 충돌이 없고 복구하면 고친 내용이 남는 정도라 허용한다.
	 * 막아야 하면 수정용 조회만 FOR UPDATE로 잠근다. 변경 감지는 그대로 두고, 휴지통 이동이 먼저 끝난 경우 TEMPLATE404_2를 낼 수 있다.
	 */
	@Transactional
	public TemplateUpdateResult update(TemplateUpdateCommand command) {
		Template template = templateQueryService.getActiveTemplate(command.userId(), command.templateId());

		TemplateTitle title = TemplateTitle.create(command.title());
		Shortcut shortcut = Shortcut.create(command.shortcut());
		validateShortcutAvailable(command.userId(), shortcut, template.getId());
		TemplateContent content = TemplateContent.create(command.content());

		template.edit(title, shortcut, content, LocalDateTime.now(clock));

		return TemplateUpdateResult.from(save(template));
	}

	/**
	 * 템플릿 사용 기록. 최근 사용 시각(last_used_at)만 Clock 시각으로 바꾸고 수정 시각(edited_at)은 그대로 둔다.
	 * 없거나 다른 회원 것이면 TEMPLATE404_1, 휴지통에 있으면 TEMPLATE404_2. 개수가 바뀌지 않으므로 회원을 잠그지 않는다.
	 * 변경 감지로 쓴다. @DynamicUpdate라 UPDATE에는 last_used_at과 감사 기능이 채우는 updated_at만 들어가고,
	 * 읽은 뒤 다른 요청이 고친 이름·본문을 덮어쓰지 않는다. 엔티티를 읽으므로 본문 복호화에 실패하는 행이면 500이다.
	 * 조회 뒤 커밋 전에 휴지통으로 옮겨지면 휴지통 행의 last_used_at이 바뀐다. 표시하지 않는 값이고 복구 시 그대로 남는 정도라 허용한다.
	 */
	@Transactional
	public TemplateUsageResult recordUsage(TemplateUsageCommand command) {
		Template template = templateQueryService.getActiveTemplate(command.userId(), command.templateId());

		template.markUsed(LocalDateTime.now(clock));

		return TemplateUsageResult.from(template);
	}

	private static void requireNoOuterTransaction() {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("갤러리 저장은 호출자 트랜잭션 밖에서 불러야 한다");
		}
	}

	/*
	 * 아래 패키지 범위 메서드 3개는 휴지통 복구(TrashCommandService)가 같이 쓴다. 생성·갤러리 저장과 규칙이 같아야 하기 때문이다.
	 * 셋 다 트랜잭션을 따로 열지 않으므로, 호출자가 lockUser를 첫 조회로 부른 트랜잭션 안에서 불러야 한다.
	 * save는 엔티티를 받고 돌려주고, 순서를 지키지 않으면 한도를 넘을 수 있어 template 도메인 안에서만 부른다
	 * (패키지 범위라 service 패키지 밖에서는 부를 수 없다).
	 */

	/** 활성 템플릿(휴지통 제외)이 한도 이상이면 TEMPLATE403_1 */
	void validateTemplateLimit(Long userId) {
		if (templateRepository.countByUserIdAndDeletedAtIsNull(userId) >= UsageLimits.TEMPLATE_LIMIT) {
			throw new BusinessException(TemplateErrorCode.TEMPLATE_LIMIT_REACHED);
		}
	}

	/** 활성 템플릿과 겹치지 않으면 원본, 겹치면 자동 번호를 붙인 단축어. 2~99가 모두 차면 TEMPLATE409_2 */
	Shortcut assignShortcut(Long userId, Shortcut original) {
		Set<String> usedKeys = templateRepository.findActiveShortcutKeys(userId, original.candidateKeys());
		return original.findAvailable(usedKeys)
			.orElseThrow(() -> new BusinessException(TemplateErrorCode.SHORTCUT_NUMBER_EXHAUSTED));
	}

	private void validateShortcutAvailable(Long userId, Shortcut shortcut) {
		if (templateRepository.existsByUserIdAndActiveShortcutKey(userId, shortcut.getKey())) {
			throw new BusinessException(TemplateErrorCode.SHORTCUT_DUPLICATED);
		}
	}

	private void validateShortcutAvailable(Long userId, Shortcut shortcut, Long excludeTemplateId) {
		if (templateRepository.existsByUserIdAndActiveShortcutKeyAndIdNot(userId, shortcut.getKey(), excludeTemplateId)) {
			throw new BusinessException(TemplateErrorCode.SHORTCUT_DUPLICATED);
		}
	}

	/**
	 * 미리 조회를 통과해도 유니크 제약 위반은 날 수 있다. 회원 잠금은 생성·복구·갤러리 저장끼리만 줄을 세우므로,
	 * 잠금을 잡지 않는 수정 요청이 같은 단축어로 바꾸면 조회와 저장 사이에 끼어든다. 그 경우도 TEMPLATE409_1로 바꾼다.
	 * 단축어 유니크가 아닌 무결성 위반은 코드 실수라 그대로 던진다 (500).
	 * 위반 예외 메시지에는 겹친 단축어가 들어 있으므로 원인으로 붙이거나 로그에 남기지 않는다.
	 */
	Template save(Template template) {
		try {
			return templateRepository.saveAndFlush(template);
		} catch (DataIntegrityViolationException e) {
			if (isActiveShortcutDuplicate(e)) {
				throw new BusinessException(TemplateErrorCode.SHORTCUT_DUPLICATED);
			}
			throw e;
		}
	}

	/**
	 * 에러 메시지를 해석하지 않고 하이버네이트가 정리한 종류와 제약 이름으로 판단한다.
	 * MySQL은 제약 이름 앞에 테이블 이름을 붙여 알려 주므로(templates.uk_...) 마지막 "." 뒤만 정확히 비교한다.
	 * 이 모양은 TemplateRepositoryTest가 실제 MySQL로 고정한다.
	 */
	private static boolean isActiveShortcutDuplicate(DataIntegrityViolationException e) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				return violation.getKind() == ConstraintKind.UNIQUE
					&& ACTIVE_SHORTCUT_UNIQUE.equals(withoutTablePrefix(violation.getConstraintName()));
			}
		}
		return false;
	}

	private static String withoutTablePrefix(String constraintName) {
		if (constraintName == null) {
			return null;
		}
		return constraintName.substring(constraintName.lastIndexOf('.') + 1);
	}

}
