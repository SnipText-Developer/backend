package com.sniptext.architecture.fixture.domain.alpha.controller;

import com.sniptext.global.security.LoginUser;

// 허용: @LoginUser를 Long에 쓰고, 애너테이션 없는 다른 타입 파라미터는 상관없다
public class CleanLoginUserController {

	public void boxedLong(@LoginUser Long userId, long page) {
	}

}
