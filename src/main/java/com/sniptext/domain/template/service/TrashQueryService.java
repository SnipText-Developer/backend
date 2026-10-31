package com.sniptext.domain.template.service;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.exception.TrashErrorCode;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.template.service.dto.query.TrashListQuery;
import com.sniptext.domain.template.service.dto.result.TrashListResult;
import com.sniptext.domain.template.service.dto.result.TrashTemplateResult;
import com.sniptext.domain.template.support.TrashCursor;
import com.sniptext.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrashQueryService {

	private final TemplateRepository templateRepository;

	/** 요청한 개수보다 1개 더 읽어 다음 페이지가 있는지 판단한다 */
	public TrashListResult getTrash(TrashListQuery query) {
		List<Template> rows = findPageWithOneMore(query);
		boolean hasNext = rows.size() > query.size();
		List<Template> page = hasNext ? rows.subList(0, query.size()) : rows;

		return TrashListResult.of(
			templateRepository.countByUserIdAndDeletedAtIsNotNull(query.userId()),
			page.stream().map(TrashTemplateResult::from).toList(),
			hasNext ? nextCursor(page.getLast()) : null
		);
	}

	private List<Template> findPageWithOneMore(TrashListQuery query) {
		Limit limit = Limit.of(query.size() + 1);
		if (query.cursor() == null || query.cursor().isEmpty()) {
			return templateRepository.findTrashFirstPage(query.userId(), limit);
		}
		TrashCursor cursor = TrashCursor.decode(query.cursor())
			.orElseThrow(() -> new BusinessException(TrashErrorCode.INVALID_PAGE_REQUEST));
		return templateRepository.findTrashAfter(query.userId(), cursor.deletedAt(), cursor.id(), limit);
	}

	private static String nextCursor(Template last) {
		return new TrashCursor(last.getDeletedAt(), last.getId()).encode();
	}

}
