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
    AUTH_GOOGLE_TOKEN_INVALID       ("IAM_004", "Google ID token không hợp lệ hoặc đã hết hạn.", 401),
    AUTH_GOOGLE_EMAIL_NOT_VERIFIED  ("IAM_005", "Email Google chưa được xác minh.", 401),
    AUTH_INVALID_CURRENT_PASSWORD  ("IAM_010", "Mật khẩu hiện tại không đúng.", 400),
    AUTH_PASSWORD_REUSED           ("IAM_011", "Mật khẩu mới phải khác mật khẩu hiện tại.", 400),
    AUTH_ACCOUNT_NOT_VERIFIED      ("IAM_012", "Tài khoản chưa được xác minh.", 403),
    AUTH_LOGIN_FAILED              ("IAM_013", "Email hoặc mật khẩu không đúng.", 401),
    AUTH_RESET_TOKEN_INVALID       ("IAM_014", "Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.", 400),
    AUTH_OAUTH_USER_NO_PASSWORD    ("IAM_015", "Tài khoản OAuth không có mật khẩu.", 400),
    AUTH_TOKEN_INVALID             ("IAM_016", "Token không hợp lệ.", 401),
    PASSWORD_RESET_COOLDOWN        ("IAM_017", "Vui lòng chờ trước khi yêu cầu đặt lại mật khẩu.", 429),
    SEEDER_ROLE_NOT_FOUND          ("IAM_018", "Role mặc định không tồn tại trong DB.", 500),
    USER_NAME_EXISTS               ("IAM_019", "Username đã được sử dụng.", 409),
    EMAIL_ALREADY_REGISTERED_AUTH  ("IAM_020", "Email đã được đăng ký.", 409),
    AUTH_OTP_INVALID               ("IAM_021", "Mã OTP không đúng.", 400),
    AUTH_OTP_EXPIRED               ("IAM_022", "Mã OTP đã hết hạn hoặc không tồn tại.", 400),
    AUTH_OTP_LOCKED                ("IAM_023", "Tài khoản tạm thời bị khóa do nhập sai OTP quá nhiều lần.", 429),

    UNAUTHORIZED ("SYS_002", "Cần xác thực.", 401),
    FORBIDDEN    ("SYS_003", "Không có quyền truy cập.", 403),

    ORDER_NOT_FOUND      ("ORD_001", "Không tìm thấy đơn hàng.", 404),
    OUT_OF_STOCK         ("INV_001", "Sản phẩm đã hết hàng trong kho.", 400);

    private final String code;
    private final String message;
    private final int httpStatus;
}
