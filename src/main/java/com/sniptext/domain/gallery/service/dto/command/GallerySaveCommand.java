package com.sniptext.domain.gallery.service.dto.command;

public record GallerySaveCommand(
	Long userId,
	Long galleryTemplateId
) {
}
