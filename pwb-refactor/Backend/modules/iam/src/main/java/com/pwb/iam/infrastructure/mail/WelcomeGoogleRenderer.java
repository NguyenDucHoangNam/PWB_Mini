package com.pwb.iam.infrastructure.mail;

import com.pwb.infra.mail.api.EmailTemplate;
import com.pwb.infra.mail.renderer.EmailTemplateRenderer;
import com.pwb.infra.mail.support.HtmlEscape;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WelcomeGoogleRenderer implements EmailTemplateRenderer {

    @Override
    public String name() {
        return EmailTemplate.WELCOME_GOOGLE.name();
    }

    @Override
    public String render(Map<String, String> variables, String locale) {
        String displayName = HtmlEscape.text(variables.getOrDefault("displayName", "bạn"));
        return "<html><body style=\"font-family:Arial,sans-serif\">"
                + "<h2>Chào mừng sếp đến với PWB</h2>"
                + "<p>Chào " + displayName + ",</p>"
                + "<p>Tài khoản của sếp đã được đăng ký thành công qua Google. Sếp có thể sử dụng tài khoản Google để đăng nhập PWB ngay bây giờ.</p>"
                + "<p>Nếu sếp cần hỗ trợ, vui lòng phản hồi email này.</p>"
                + "</body></html>";
    }

    @Override
    public String subject(Map<String, String> variables, String locale) {
        return "Chào mừng sếp đến với PWB";
    }
}
