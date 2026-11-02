package com.sniptext.domain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** 회원 조회 (다른 도메인이 회원 정보를 읽을 때 쓰는 진입점). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

	private final UserRepository userRepository;

}
