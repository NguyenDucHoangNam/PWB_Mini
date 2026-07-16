package com.pwb.backend.service;

import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.StringReader;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SsmlSanitizer {

    public static final int MAX_DEPTH = 3;

    private static final Set<String> ALLOWED_TAGS = Set.of(
            "speak", "say-as", "phoneme", "break", "prosody", "emphasis");

    private static final Set<String> ALLOWED_ATTRIBUTES = Set.of(
            "interpret-as", "time", "rate", "pitch", "volume", "level", "alphabet", "ph");

    private static final Pattern PLAIN_TEXT_SSML_TAG = Pattern.compile("<\\s*speak[^>]*>", Pattern.CASE_INSENSITIVE);

    private final DocumentBuilderFactory safeFactory;

    public SsmlSanitizer() {
        this.safeFactory = DocumentBuilderFactory.newInstance();
        try {
            this.safeFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            this.safeFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            this.safeFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            this.safeFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            this.safeFactory.setXIncludeAware(false);
            this.safeFactory.setExpandEntityReferences(false);
        } catch (ParserConfigurationException ex) {
            throw new IllegalStateException("Failed to configure safe XML parser", ex);
        }
    }

    public String wrapAsSsml(String textOrSsml) {
        if (textOrSsml == null) {
            return "<speak></speak>";
        }
        String trimmed = textOrSsml.trim();
        if (trimmed.toLowerCase().startsWith("<speak")) {
            return trimmed;
        }
        return "<speak>" + escapeXmlText(trimmed) + "</speak>";
    }

    public String sanitize(String input) {
        String ssml = wrapAsSsml(input);
        validate(ssml);
        return ssml;
    }

    public String stripSsmlToRawText(String ssml) {
        if (ssml == null || ssml.isBlank()) {
            return "";
        }
        return ssml.replaceAll("<[^>]+>", "").trim();
    }

    private void validate(String ssml) {
        try {
            DocumentBuilder builder = safeFactory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            var document = builder.parse(new InputSource(new StringReader(ssml)));
            var root = document.getDocumentElement();
            validateNode(root, 1);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("SSML parsing failed: {}", ex.getMessage());
            throw new BusinessException(
                    ErrorCode.INVALID_SSML_TAG,
                    "Malformed SSML content: " + ex.getMessage());
        }
    }

    private void validateNode(org.w3c.dom.Node node, int depth) {
        if (depth > MAX_DEPTH) {
            throw new BusinessException(
                    ErrorCode.INVALID_SSML_TAG,
                    "SSML nesting exceeds maximum depth of " + MAX_DEPTH);
        }

        if (node.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
            return;
        }

        String tagName = node.getNodeName().toLowerCase();
        if (!ALLOWED_TAGS.contains(tagName)) {
            throw new BusinessException(
                    ErrorCode.INVALID_SSML_TAG,
                    "Unsupported SSML tag: <" + tagName + ">");
        }

        org.w3c.dom.NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                org.w3c.dom.Node attr = attributes.item(i);
                if (!ALLOWED_ATTRIBUTES.contains(attr.getNodeName().toLowerCase())) {
                    throw new BusinessException(
                            ErrorCode.INVALID_SSML_TAG,
                            "Unsupported attribute: " + attr.getNodeName());
                }
            }
        }

        org.w3c.dom.NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            validateNode(children.item(i), depth + 1);
        }
    }

    private String escapeXmlText(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    @SuppressWarnings("unused")
    private static Matcher plainTextMatcher() {
        return PLAIN_TEXT_SSML_TAG.matcher("");
    }
}
