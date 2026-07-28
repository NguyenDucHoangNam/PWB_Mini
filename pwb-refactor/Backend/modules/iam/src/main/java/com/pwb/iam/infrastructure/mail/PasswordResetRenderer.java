package com.pwb.iam.infrastructure.mail;

import com.pwb.infra.mail.api.EmailTemplate;
import com.pwb.infra.mail.renderer.EmailTemplateRenderer;
import com.pwb.infra.mail.support.HtmlEscape;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PasswordResetRenderer implements EmailTemplateRenderer {

    @Override
    public String name() {
        return EmailTemplate.PASSWORD_RESET.name();
    }

    @Override
    public String render(Map<String, String> variables, String locale) {
        String link = HtmlEscape.url(variables.getOrDefault("resetLink", "#"));
        return "<html><body style=\"font-family:Arial,sans-serif\">"
                + "<h2>Đặt lại mật khẩu PWB</h2>"
                + "<p>Sếp vừa yêu cầu đặt lại mật khẩu. Nhấn nút bên dưới để tiếp tục:</p>"
                + "<p><a href=\"" + link + "\" style=\"display:inline-block;padding:10px 20px;background:#2563eb;color:#fff;text-decoration:none;border-radius:6px\">Đặt lại mật khẩu</a></p>"
                + "<p>Nếu không phải sếp thực hiện, vui lòng bỏ qua email này.</p>"
                + "</body></html>";
    }

    @Override
    public String subject(Map<String, String> variables, String locale) {
        return "Đặt lại mật khẩu PWB";
    }
}