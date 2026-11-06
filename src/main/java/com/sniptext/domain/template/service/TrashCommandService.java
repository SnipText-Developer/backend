package com.sniptext.domain.template.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.template.entity.Shortcut;
import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.exception.TrashErrorCode;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.template.service.dto.command.TrashPurgeCommand;
import com.sniptext.domain.template.service.dto.command.TrashRestoreCommand;
import com.sniptext.domain.template.service.dto.result.TemplateShortcutAssignResult;
import com.sniptext.domain.template.service.dto.result.TrashEmptyResult;
import com.sniptext.domain.template.service.dto.result.TrashPurgeResult;
import com.sniptext.domain.user.service.UserCommandService;
import com.sniptext.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TrashCommandService {

	private final TemplateRepository templateRepository;

	private final UserCommandService userCommandService;

	private final TemplateCommandService templateCommandService;

	/**
	 * 휴지통 템플릿을 내 템플릿으로 되돌린다. 한도·자동 번호·저장 규칙은 생성·갤러리 저장과 같다.
	 * 휴지통에 없으면 TRASH404_1, 한도 도달이면 TEMPLATE403_1, 번호 2~99가 모두 차면 TEMPLATE409_2 (앞의 것이 먼저다).
	 * 회원 잠금이 트랜잭션의 첫 조회여야 한다. MySQL 기본 격리 수준(REPEATABLE READ)은 첫 일반 조회 시점의 스냅샷을 끝까지 쓰므로,
	 * 템플릿을 먼저 읽으면 잠금을 기다리는 사이 커밋된 생성·복구를 개수 검사와 단축어 조회가 보지 못한다.
	 * 같은 템플릿을 동시에 복구해도 잠금으로 줄을 서므로, 뒤에 온 요청은 이미 꺼낸 템플릿을 읽어 TRASH404_1이 된다.
	 * 템플릿 행도 잠가 읽는다. 정리 배치·영구 삭제는 회원을 잠그지 않아, 일반 조회로 읽으면 저장 전에 행이 지워져 500이 나기 때문이다.
	 * 잠금 조회는 스냅샷을 만들지 않으므로 이어지는 개수 검사가 옛 값을 보는 문제는 생기지 않는다.
	 * 엔티티를 읽으므로 본문 복호화에 실패하는 행은 복구도 500이다.
	 */
	@Transactional
	public TemplateShortcutAssignResult restore(TrashRestoreCommand command) {
		userCommandService.lockUser(command.userId());

		Template template = templateRepository.findTrashedByIdAndUserIdForUpdate(command.templateId(), command.userId())
			.orElseThrow(() -> new BusinessException(TrashErrorCode.TRASHED_TEMPLATE_NOT_FOUND));

		templateCommandService.validateTemplateLimit(command.userId());

		Shortcut original = template.getShortcut();
		template.restore(templateCommandService.assignShortcut(command.userId(), original));

		return TemplateShortcutAssignResult.of(templateCommandService.save(template), original);
	}

	/**
	 * 본인 휴지통의 템플릿 하나를 영구 삭제한다. 없는 ID, 다른 회원의 템플릿, 휴지통 밖 템플릿, 이미 지운 템플릿은 TRASH404_1.
	 * 엔티티를 읽지 않고 삭제 쿼리로 지우므로 본문 복호화에 실패하는 행도 지운다.
	 * 같은 템플릿을 동시에 지우거나 복구와 겹치면 행 잠금으로 줄을 서고, 뒤에 온 쪽은 조건에 맞는 행이 없어 TRASH404_1이 된다.
	 */
	@Transactional
	public TrashPurgeResult purge(TrashPurgeCommand command) {
		int deleted = templateRepository.deleteTrashedByIdAndUserId(command.templateId(), command.userId());
		if (deleted == 0) {
			throw new BusinessException(TrashErrorCode.TRASHED_TEMPLATE_NOT_FOUND);
		}
		return new TrashPurgeResult(command.templateId());
	}

	/** 본인 휴지통을 모두 영구 삭제하고 지운 개수를 돌려준다. 비어 있어도 성공이다. 읽지 않고 지우는 이유는 purge와 같다 */
	@Transactional
	public TrashEmptyResult empty(Long userId) {
		return new TrashEmptyResult(templateRepository.deleteTrashedByUserId(userId));
	}

}
