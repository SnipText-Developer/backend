package com.sniptext.architecture.fixture.domain.alpha.controller;

import com.sniptext.global.security.LoginUser;

// 위반: @LoginUser를 Long이 아닌 타입에 쓴다
public class LoginUserMisuseController {

	public void primitiveLong(@LoginUser long userId) {
	}

	public void boxedInteger(String keyword, @LoginUser Integer userId) {
	}

}
