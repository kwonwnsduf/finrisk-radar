package com.finrisk.radar.global.response;

import com.finrisk.radar.global.error.ErrorCode;

public record ApiResponse<T>(
		boolean success,
		String code,
		String message,
		T data
) {
	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, "SUCCESS", "Request succeeded.", data);
	}

	public static <T> ApiResponse<T> error(ErrorCode errorCode, T data) {
		return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), data);
	}
}
