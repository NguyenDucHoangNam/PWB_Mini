package com.pwb.infra.mail.renderer;

import java.util.Map;

public interface EmailTemplateRenderer {

    String name();

    String render(Map<String, String> variables, String locale);

    String subject(Map<String, String> variables, String locale);
}