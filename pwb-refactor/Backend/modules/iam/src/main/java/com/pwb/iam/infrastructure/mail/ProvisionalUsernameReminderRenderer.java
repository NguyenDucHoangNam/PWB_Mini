package com.pwb.iam.infrastructure.mail;

import com.pwb.infra.mail.api.EmailTemplate;
import com.pwb.infra.mail.renderer.EmailTemplateRenderer;
import com.pwb.infra.mail.support.HtmlEscape;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ProvisionalUsernameReminderRenderer implements EmailTemplateRenderer {

    @Override
    public String name() {
        return EmailTemplate.PROVISIONAL_USERNAME_REMINDER.name();
    }

    @Override
    public String render(Map<String, String> variables, String locale) {
        String username = HtmlEscape.text(variables.getOrDefault("currentUsername", ""));
        String updateLink = HtmlEscape.url(variables.getOrDefault("updateLink", "#"));
        return "<html><body style=\"font-family:Arial,sans-serif\">"
                + "<h2>Nhắc nhở cập nhật tên đăng nhập</h2>"
                + "<p>Sếp hiện đang sử dụng tên đăng nhập tạm: <strong>" + username + "</strong></p>"
                + "<p>Vui lòng cập nhật tên đăng nhập chính thức để hoàn tất hồ sơ:</p>"
                + "<p><a href=\"" + updateLink + "\" style=\"display:inline-block;padding:10px 20px;background:#2563eb;color:#fff;text-decoration:none;border-radius:6px\">Cập nhật ngay</a></p>"
                + "</body></html>";
    }

    @Override
    public String subject(Map<String, String> variables, String locale) {
        return "Nhắc nhở cập nhật tên đăng nhập PWB";
    }
}
