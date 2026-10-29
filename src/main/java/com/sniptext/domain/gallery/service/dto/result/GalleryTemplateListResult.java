package com.sniptext.domain.gallery.service.dto.result;

import java.util.List;

public record GalleryTemplateListResult(int totalCount, List<GalleryTemplateResult> templates) {

	public static GalleryTemplateListResult from(List<GalleryTemplateResult> templates) {
		return new GalleryTemplateListResult(templates.size(), List.copyOf(templates));
	}

}
