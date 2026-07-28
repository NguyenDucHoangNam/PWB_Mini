package com.pwb.iam.infrastructure.mail;

import com.pwb.infra.mail.api.EmailTemplate;
import com.pwb.infra.mail.renderer.EmailTemplateRenderer;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OtpRegisterRenderer implements EmailTemplateRenderer {

    @Override
    public String name() {
        return EmailTemplate.OTP_REGISTER.name();
    }

    @Override
    public String render(Map<String, String> variables, String locale) {
        String code = variables.getOrDefault("code", "******");
        String ttl = variables.getOrDefault("ttlMinutes", "10");
        return "<html><body style=\"font-family:Arial,sans-serif\">"
                + "<h2>Xác thực tài khoản PWB</h2>"
                + "<p>Mã OTP của sếp là:</p>"
                + "<h1 style=\"color:#2563eb;letter-spacing:4px\">" + code + "</h1>"
                + "<p>Mã có hiệu lực trong " + ttl + " phút.</p>"
                + "</body></html>";
    }

    @Override
    public String subject(Map<String, String> variables, String locale) {
        return "Mã xác thực đăng ký PWB";
    }
}