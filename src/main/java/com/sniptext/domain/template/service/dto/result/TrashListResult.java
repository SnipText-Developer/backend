package com.sniptext.domain.template.service.dto.result;

import java.util.List;

public record TrashListResult(long totalCount, List<TrashTemplateResult> templates, boolean hasNext,
	String nextCursor) {

	public static TrashListResult of(long totalCount, List<TrashTemplateResult> templates, String nextCursor) {
		return new TrashListResult(totalCount, List.copyOf(templates), nextCursor != null, nextCursor);
	}

}
