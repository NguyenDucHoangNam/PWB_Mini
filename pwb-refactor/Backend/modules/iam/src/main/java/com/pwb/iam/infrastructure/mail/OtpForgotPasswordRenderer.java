package com.pwb.iam.infrastructure.mail;

import com.pwb.infra.mail.api.EmailTemplate;
import com.pwb.infra.mail.renderer.EmailTemplateRenderer;
import com.pwb.infra.mail.support.HtmlEscape;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OtpForgotPasswordRenderer implements EmailTemplateRenderer {

    @Override
    public String name() {
        return EmailTemplate.OTP_FORGOT_PASSWORD.name();
    }

    @Override
    public String render(Map<String, String> variables, String locale) {
        String code = HtmlEscape.text(variables.getOrDefault("code", "******"));
        String ttl = HtmlEscape.text(variables.getOrDefault("ttlMinutes", "10"));
        return "<html><body style=\"font-family:Arial,sans-serif\">"
                + "<h2>Mã xác thực đặt lại mật khẩu</h2>"
                + "<p>Sếp vừa yêu cầu đặt lại mật khẩu. Mã OTP của sếp là:</p>"
                + "<h1 style=\"color:#2563eb;letter-spacing:4px\">" + code + "</h1>"
                + "<p>Mã có hiệu lực trong " + ttl + " phút. Nếu không phải sếp thực hiện, vui lòng bỏ qua email này.</p>"
                + "</body></html>";
    }

    @Override
    public String subject(Map<String, String> variables, String locale) {
        return "Mã xác thực đặt lại mật khẩu PWB";
    }
}
