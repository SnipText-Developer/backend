package com.sniptext.domain.template.service.dto.result;

/** 휴지통 비우기로 지운 개수. 비어 있었으면 0 */
public record TrashEmptyResult(int deletedCount) {
}
