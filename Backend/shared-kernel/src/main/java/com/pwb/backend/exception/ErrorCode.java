package com.pwb.backend.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    INTERNAL_SERVER_ERROR("SYS_000", "Lỗi hệ thống không xác định.", 500),
    INVALID_INPUT        ("SYS_001", "Dữ liệu đầu vào không hợp lệ.", 400),

    EMAIL_ALREADY_EXISTS ("IAM_001", "Email đã được đăng ký.", 409),
    WEAK_PASSWORD        ("IAM_002", "Mật khẩu không đủ mạnh.", 400),
    USER_NOT_FOUND       ("IAM_003", "Không tìm thấy người dùng.", 404),

    ORDER_NOT_FOUND      ("ORD_001", "Không tìm thấy đơn hàng.", 404),
    OUT_OF_STOCK         ("INV_001", "Sản phẩm đã hết hàng trong kho.", 400);

    private final String code;
    private final String message;
    private final int httpStatus;
}
