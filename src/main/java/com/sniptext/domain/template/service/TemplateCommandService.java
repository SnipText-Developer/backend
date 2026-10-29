package com.sniptext.domain.template.service;

import org.hibernate.exception.ConstraintViolationException;
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
	 * 한도 도달이면 TEMPLATE403_1, 활성 단축어가 겹치면 TEMPLATE409_1.
	 */
	@Transactional
	public TemplateCreateResult create(TemplateCreateCommand command) {
		userCommandService.lockUser(command.userId());
		validateTemplateLimit(command.userId());

		TemplateTitle title = TemplateTitle.create(command.title());
		Shortcut shortcut = Shortcut.create(command.shortcut());
		TemplateContent content = TemplateContent.create(command.content());
		Template template = Template.create(command.userId(), title, shortcut, content);
		return TemplateCreateResult.from(save(template));
	}

	/** lockUser를 먼저 부른 같은 트랜잭션 안에서만 부른다. 개수는 countActive와 같은 쿼리다 */
	private void validateTemplateLimit(Long userId) {
		int templateLimit = planQueryService.getCurrentPlan(userId).templateLimit();
		if (templateRepository.countByUserIdAndDeletedAtIsNull(userId) >= templateLimit) {
			throw new BusinessException(TemplateErrorCode.TEMPLATE_LIMIT_REACHED);
		}
	}

	/**
	 * 단축어 중복은 미리 조회하지 않고 유니크 제약으로 판정한다 (조회와 저장 사이에 끼어드는 요청까지 막기 위해서다).
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

	// MySQL은 제약 이름 앞에 테이블 이름을 붙여 알려 주므로(templates.uk_...) 끝부분으로 비교한다
	private static boolean isActiveShortcutDuplicate(DataIntegrityViolationException e) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				String constraintName = violation.getConstraintName();
				return constraintName != null && constraintName.endsWith(ACTIVE_SHORTCUT_UNIQUE);
			}
		}
		return false;
	}

}
