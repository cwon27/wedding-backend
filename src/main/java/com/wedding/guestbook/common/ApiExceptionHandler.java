package com.wedding.guestbook.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** 오류 응답 형식: { "code": "...", "message": "..." } */
@RestControllerAdvice
public class ApiExceptionHandler {

	public record ErrorResponse(String code, String message) {
	}

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ErrorResponse> handle(ApiException e) {
		return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getCode(), e.getMessage()));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ErrorResponse> handle(MaxUploadSizeExceededException e) {
		return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
				.body(new ErrorResponse("PHOTO_TOO_LARGE", "사진 용량이 너무 커요. 10MB 이하로 올려 주세요."));
	}
}
