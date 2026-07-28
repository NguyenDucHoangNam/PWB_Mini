package com.pwb.iam.domain.audit;

public enum AuditEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_CHANGED,
    GOOGLE_LOGIN_SUCCESS
}