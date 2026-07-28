package com.pwb.infra.mail.support;

public final class HtmlEscape {

    private HtmlEscape() {
    }

    public static String text(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    public static String url(String input) {
        if (input == null) {
            return "";
        }
        String trimmed = input.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") && !trimmed.startsWith("mailto:")) {
            return "#";
        }
        return text(trimmed);
    }
}