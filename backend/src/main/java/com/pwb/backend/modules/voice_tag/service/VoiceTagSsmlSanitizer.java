package com.pwb.backend.modules.voice_tag.service;

import java.io.IOException;
import org.xml.sax.SAXException;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.voice_tag.config.VoiceTagProperties;
import com.pwb.backend.modules.voice_tag.exception.VoiceTagErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

@Slf4j
@Component
public class VoiceTagSsmlSanitizer {

    private static final String SSML_ROOT = "speak";
    private static final String SSML_WRAPPER_FORMAT = "<speak>%s</speak>";
    private static final Set<String> ALLOWED_TAGS = Set.of(
            "speak", "say-as", "phoneme", "break", "prosody", "emphasis");
    private static final Set<String> ALLOWED_ATTRIBUTES = Set.of(
            "interpret-as", "time", "rate", "pitch", "volume", "level", "alphabet", "ph");

    private final VoiceTagProperties properties;

    public VoiceTagSsmlSanitizer(VoiceTagProperties properties) {
        this.properties = properties;
    }

    public SanitizedSsml sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(VoiceTagErrorCode.INVALID_SSML_TAG);
        }
        if (raw.length() > properties.getMaxSsmlLength()) {
            throw new BusinessException(VoiceTagErrorCode.INVALID_SSML_TAG);
        }

        if (!raw.contains("<")) {
            return sanitizePlainText(raw);
        }

        Document doc = parseStrict(raw);
        walkAndValidate(doc.getDocumentElement(), 0);

        String serialized = serialize(doc);
        int rawTextLength = extractPlainTextLength(doc);

        if (rawTextLength > properties.getMaxRawTextLength()) {
            throw new BusinessException(VoiceTagErrorCode.TTS_TEXT_TOO_LONG);
        }

        return new SanitizedSsml(serialized, rawTextLength);
    }

    private SanitizedSsml sanitizePlainText(String raw) {
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            throw new BusinessException(VoiceTagErrorCode.INVALID_SSML_TAG);
        }
        if (trimmed.length() > properties.getMaxRawTextLength()) {
            throw new BusinessException(VoiceTagErrorCode.TTS_TEXT_TOO_LONG);
        }
        String ssml = String.format(SSML_WRAPPER_FORMAT, escapeXml(trimmed));
        return new SanitizedSsml(ssml, trimmed.length());
    }

    private Document parseStrict(String xml) {
        String normalized = wrapIfPlainText(xml);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(normalized)));
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.warn("SSML parsing failed: inputLen={} reason={}", xml.length(), ex.getMessage());
            throw new BusinessException(VoiceTagErrorCode.INVALID_SSML_TAG);
        }
    }

    private String wrapIfPlainText(String xml) {
        if (xml == null) {
            return "<speak></speak>";
        }
        String stripped = stripBom(xml).strip();
        return String.format(SSML_WRAPPER_FORMAT, escapeXml(stripped));
    }

    private String stripBom(String input) {
        return (!input.isEmpty() && input.charAt(0) == '﻿') ? input.substring(1) : input;
    }

    private String escapeXml(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> {
                    if (c >= 0x20 || c == '\n' || c == '\r' || c == '\t') {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private void walkAndValidate(Element element, int depth) {
        if (depth > properties.getMaxNestedDepth()) {
            throw new BusinessException(VoiceTagErrorCode.INVALID_SSML_TAG);
        }
        if (!ALLOWED_TAGS.contains(element.getTagName())) {
            log.warn("SSML tag rejected: tagName={}", element.getTagName());
            throw new BusinessException(VoiceTagErrorCode.INVALID_SSML_TAG);
        }

        var attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String attrName = attr.getNodeName();
            if (!ALLOWED_ATTRIBUTES.contains(attrName)) {
                log.warn("SSML attribute rejected: tagName={}, attr={}", element.getTagName(), attrName);
                throw new BusinessException(VoiceTagErrorCode.INVALID_SSML_TAG);
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) {
                walkAndValidate(childElement, depth + 1);
            }
        }
    }

    private String serialize(Document doc) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (Exception ex) {
            throw new BusinessException(VoiceTagErrorCode.INVALID_SSML_TAG);
        }
    }

    private int extractPlainTextLength(Document doc) {
        StringBuilder sb = new StringBuilder();
        NodeList nodes = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.TEXT_NODE) {
                sb.append(node.getTextContent());
            } else if (node.getNodeType() == Node.ELEMENT_NODE && SSML_ROOT.equals(node.getNodeName())) {
                sb.append(extractText(node));
            }
        }
        return sb.toString().trim().length();
    }

    private String extractText(Node node) {
        StringBuilder sb = new StringBuilder();
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                sb.append(child.getTextContent());
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                sb.append(extractText(child));
            }
        }
        return sb.toString();
    }
}
