package com.sniptext.global.client.google;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 구글 코드 교환 클라이언트를 연결·응답 시간 제한(GoogleProperties)을 건 RestClient로 만든다. */
@Configuration
public class GoogleClientConfig {

	@Bean
	public GoogleAuthCodeClient googleAuthCodeClient(GoogleProperties properties,
		GoogleIdTokenVerifier idTokenVerifier) {
		return new GoogleAuthCodeClient(RestClient.builder().requestFactory(requestFactory(properties)), properties,
			idTokenVerifier);
	}

	static JdkClientHttpRequestFactory requestFactory(GoogleProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		return requestFactory;
	}

}
