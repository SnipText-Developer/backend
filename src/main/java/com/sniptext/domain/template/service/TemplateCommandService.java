package com.sniptext.domain.template.service;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.plan.service.PlanQueryService;
import com.sniptext.domain.template.entity.Shortcut;
import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.entity.TemplateContent;
import com.sniptext.domain.template.entity.TemplateTitle;
import com.sniptext.domain.template.exception.TemplateErrorCode;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.template.service.dto.command.TemplateCreateCommand;
import com.sniptext.domain.template.service.dto.result.TemplateCreateResult;
import com.sniptext.domain.user.service.UserCommandService;
import com.sniptext.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TemplateCommandService {

	private static final String ACTIVE_SHORTCUT_UNIQUE = "uk_templates_user_active_shortcut";

	private final TemplateRepository templateRepository;
	private final UserCommandService userCommandService;
	private final PlanQueryService planQueryService;

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

	private void validateTemplateLimit(Long userId) {
		int templateLimit = planQueryService.getCurrentPlan(userId).templateLimit();
		if (templateRepository.countByUserIdAndDeletedAtIsNull(userId) >= templateLimit) {
			throw new BusinessException(TemplateErrorCode.TEMPLATE_LIMIT_REACHED);
		}
	}

	private void validateShortcutAvailable(Long userId, Shortcut shortcut) {
		if (templateRepository.existsByUserIdAndActiveShortcutKey(userId, shortcut.getKey())) {
			throw new BusinessException(TemplateErrorCode.SHORTCUT_DUPLICATED);
		}
	}

	/**
	 * 미리 조회를 통과해도 유니크 제약 위반은 날 수 있다. 회원 잠금은 생성·복구·갤러리 저장끼리만 줄을 세우므로,
	 * 잠금을 잡지 않는 수정 요청이 같은 단축어로 바꾸면 조회와 저장 사이에 끼어든다. 그 경우도 TEMPLATE409_1로 바꾼다.
	 * 단축어 유니크가 아닌 무결성 위반은 코드 실수라 그대로 던진다 (500).
	 * 위반 예외 메시지에는 겹친 단축어가 들어 있으므로 원인으로 붙이거나 로그에 남기지 않는다.
	 */
	private Template save(Template template) {
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
