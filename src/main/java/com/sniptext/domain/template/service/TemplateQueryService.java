package com.sniptext.domain.template.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.template.repository.TemplateRepository;

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

}
