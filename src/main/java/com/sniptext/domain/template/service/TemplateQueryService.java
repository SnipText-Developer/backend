package com.sniptext.domain.template.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.exception.TemplateErrorCode;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.template.service.dto.query.TemplateDetailQuery;
import com.sniptext.domain.template.service.dto.result.TemplateDetailResult;
import com.sniptext.domain.template.service.dto.result.TemplateListItemResult;
import com.sniptext.domain.template.service.dto.result.TemplateListResult;
import com.sniptext.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TemplateQueryService {

	private final TemplateRepository templateRepository;

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

	/** 내 템플릿 상세. 없거나 다른 회원 것이면 TEMPLATE404_1, 휴지통에 있으면 TEMPLATE404_2 */
	public TemplateDetailResult getTemplate(TemplateDetailQuery query) {
		return TemplateDetailResult.from(getActiveTemplate(query.userId(), query.templateId()));
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

}
