package com.wedding.guestbook.common;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * 하객에게 보여줄 수 있는 오류. code 는 프론트가 분기할 때, message 는 화면에 그대로 띄울 때 쓴다.
 */
@Getter
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;

	public ApiException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public static ApiException notFound(String code, String message) {
		return new ApiException(HttpStatus.NOT_FOUND, code, message);
	}

	public static ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}

	public static ApiException badRequest(String code, String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, code, message);
	}
}
