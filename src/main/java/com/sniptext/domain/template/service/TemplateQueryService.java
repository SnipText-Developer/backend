package com.sniptext.domain.template.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.template.entity.Shortcut;
import com.sniptext.domain.template.entity.ShortcutUnavailableReason;
import com.sniptext.domain.template.entity.ShortcutViolation;
import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.exception.TemplateErrorCode;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.template.service.dto.query.TemplateDetailQuery;
import com.sniptext.domain.template.service.dto.query.TemplateShortcutAvailabilityQuery;
import com.sniptext.domain.template.service.dto.query.TemplateSyncQuery;
import com.sniptext.domain.template.service.dto.result.TemplateDetailResult;
import com.sniptext.domain.template.service.dto.result.TemplateListItemResult;
import com.sniptext.domain.template.service.dto.result.TemplateListResult;
import com.sniptext.domain.template.service.dto.result.TemplateShortcutAvailabilityResult;
import com.sniptext.domain.template.service.dto.result.TemplateSyncResult;
import com.sniptext.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TemplateQueryService {

	private final TemplateRepository templateRepository;
	private final Clock clock;

	/** 휴지통을 제외한 보유 템플릿 개수. 사용량 API가 부른다 */
	public long countActive(Long userId) {
		return templateRepository.countByUserIdAndDeletedAtIsNull(userId);
	}

	/** 내 템플릿 목록. 휴지통을 제외한 전체를 생성 순으로 */
	public TemplateListResult getTemplates(Long userId) {
		List<TemplateListItemResult> templates = templateRepository.findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(userId)
			.stream()
			.map(TemplateListItemResult::from)
			.toList();
		return TemplateListResult.from(templates);
	}

	/**
	 * 확장 템플릿 동기화 (S2 명세 27번). 보낸 버전이 지금 버전과 같으면 목록 없이, 다르거나 없으면 8번 목록 전체를 돌려준다.
	 * 버전 계산(개수·가장 늦은 updated_at)과 목록 조회는 반드시 이 메서드의 읽기 전용 트랜잭션 하나에서 한다.
	 * MySQL 기본 격리 수준(REPEATABLE READ)은 첫 조회 시점의 스냅샷을 트랜잭션 끝까지 쓰므로 세 조회가 같은 시점을 본다.
	 * 따로 읽으면 그 사이 다른 기기의 변경이 커밋될 때 "새 버전 + 옛 목록"이 나가고, 확장은 새 버전을 저장해
	 * 그 변경을 다음 변경 전까지 놓친다. 목록을 다른 트랜잭션(별도 빈 호출 등)으로 옮기지 않는다
	 */
	public TemplateSyncResult sync(TemplateSyncQuery query) {
		LocalDateTime syncedAt = LocalDateTime.now(clock);
		String version = TemplateSyncVersion.of(
			templateRepository.countByUserIdAndDeletedAtIsNull(query.userId()),
			templateRepository.findLatestUpdatedAt(query.userId()).orElse(null));

		if (version.equals(query.version())) {
			return TemplateSyncResult.unchanged(version, syncedAt);
		}
		return TemplateSyncResult.of(version, syncedAt, getTemplates(query.userId()));
	}

	/** 내 템플릿 상세. 없거나 다른 회원 것이면 TEMPLATE404_1, 휴지통에 있으면 TEMPLATE404_2 */
	public TemplateDetailResult getTemplate(TemplateDetailQuery query) {
		return TemplateDetailResult.from(getActiveTemplate(query.userId(), query.templateId()));
	}

	/**
	 * 단축어 확인. 저장과 같은 순서(NFC 정규화 → 형식 → 예약어 → 중복)로 보고, 규칙 위반도 에러 대신 사유로 돌려준다.
	 * 중복은 내 활성 템플릿(휴지통 제외)의 비교 키로 판정하고, excludeTemplateId가 있으면 그 템플릿만 뺀다.
	 * 남의 템플릿·휴지통 ID를 넘겨도 내 활성 템플릿에 없는 ID라 결과가 같으므로 따로 확인하지 않는다.
	 * 저장 순간 다른 요청이 먼저 등록할 수 있어 회원을 잠그지 않는다 (최종 검증은 저장 API의 TEMPLATE409_1).
	 */
	public TemplateShortcutAvailabilityResult checkShortcutAvailability(TemplateShortcutAvailabilityQuery query) {
		Optional<ShortcutViolation> violation = Shortcut.findViolation(query.shortcut());
		if (violation.isPresent()) {
			return TemplateShortcutAvailabilityResult.unavailable(Shortcut.normalize(query.shortcut()),
				ShortcutUnavailableReason.from(violation.get()));
		}

		Shortcut shortcut = Shortcut.create(query.shortcut());
		if (isShortcutTaken(query.userId(), shortcut, query.excludeTemplateId())) {
			return TemplateShortcutAvailabilityResult.unavailable(shortcut.getValue(),
				ShortcutUnavailableReason.DUPLICATED);
		}
		return TemplateShortcutAvailabilityResult.available(shortcut.getValue());
	}

	/**
	 * 휴지통에 없는 본인 템플릿. 수정·사용 기록도 같은 404 구분을 쓴다.
	 * 엔티티를 돌려주므로 template 도메인 안에서만 부른다 (패키지 범위라 service 패키지 밖에서는 부를 수 없다).
	 */
	Template getActiveTemplate(Long userId, Long templateId) {
		Template template = templateRepository.findByIdAndUserId(templateId, userId)
			.orElseThrow(() -> new BusinessException(TemplateErrorCode.TEMPLATE_NOT_FOUND));
		if (template.isInTrash()) {
			throw new BusinessException(TemplateErrorCode.TEMPLATE_IN_TRASH);
		}
		return template;
	}

	private boolean isShortcutTaken(Long userId, Shortcut shortcut, Long excludeTemplateId) {
		if (excludeTemplateId == null) {
			return templateRepository.existsByUserIdAndActiveShortcutKey(userId, shortcut.getKey());
		}
		return templateRepository.existsByUserIdAndActiveShortcutKeyAndIdNot(userId, shortcut.getKey(),
			excludeTemplateId);
	}

}
