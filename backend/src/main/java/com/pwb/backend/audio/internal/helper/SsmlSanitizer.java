package com.pwb.backend.audio.internal.helper;

import com.pwb.backend.audio.internal.config.AudioProperties;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SsmlSanitizer {

  private static final Set<String> ALLOWED_TAGS = Set.of(
      "speak", "say-as", "phoneme", "break", "prosody", "emphasis"
  );

  private static final Set<String> ALLOWED_ATTRS = Set.of(
      "interpret-as", "time", "rate", "pitch", "volume", "level", "alphabet", "ph"
  );

  private final AudioProperties audioProperties;

  public SanitizedSsml sanitize(String input) {
    if (input == null || input.isBlank()) {
      throw new BusinessException(ErrorCode.TTS_TEXT_TOO_LONG, "Voice tag text is required");
    }
    if (input.length() > audioProperties.getVoiceTag().getSsmlMaxLength()) {
      throw new BusinessException(ErrorCode.INVALID_SSML_TAG, "Voice tag input exceeds SSML length limit");
    }

    String stripped = stripAllTags(input);
    if (stripped.length() > audioProperties.getVoiceTag().getRawTextMaxLength()) {
      throw new BusinessException(ErrorCode.TTS_TEXT_TOO_LONG,
          "Raw voice tag text exceeds 100 characters");
    }

    Document parsed = parseIfHasTags(input);
    String sanitized = input;
    if (parsed != null) {
      validateStructure(parsed);
      sanitized = serializeDocument(parsed);
    }
    return new SanitizedSsml(sanitized, stripped);
  }

  public String stripAllTags(String input) {
    if (input == null) return "";
    return input.replaceAll("<[^>]+>", "").trim();
  }

  private Document parseIfHasTags(String input) {
    if (!input.contains("<")) return null;
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
      dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      dbf.setExpandEntityReferences(false);
      dbf.setNamespaceAware(false);
      DocumentBuilder builder = dbf.newDocumentBuilder();
      String wrapped = input.trim().startsWith("<speak")
          ? input
          : "<speak>" + input + "</speak>";
      return builder.parse(new InputSource(new StringReader(wrapped)));
    } catch (BusinessException ex) {
      throw ex;
    } catch (Exception ex) {
      log.warn("SSML parse failed: {}", ex.getMessage());
      throw new BusinessException(ErrorCode.INVALID_SSML_TAG, "SSML input is not well-formed");
    }
  }

  private void validateStructure(Document doc) {
    Element root = doc.getDocumentElement();
    if (root == null || !"speak".equals(root.getNodeName())) {
      throw new BusinessException(ErrorCode.INVALID_SSML_TAG,
          "SSML root element must be <speak>");
    }
    int maxDepth = audioProperties.getVoiceTag().getSsmlMaxDepth();
    walk(root, 1, maxDepth);
  }

  private void walk(Element element, int depth, int maxDepth) {
    if (depth > maxDepth) {
      throw new BusinessException(ErrorCode.INVALID_SSML_TAG,
          "SSML nested depth exceeds limit");
    }
    for (int i = 0; i < element.getAttributes().getLength(); i++) {
      Node attr = element.getAttributes().item(i);
      String name = attr.getNodeName();
      if (!ALLOWED_ATTRS.contains(name)) {
        log.warn("SSML tag attribute rejected: tag={}, attr={}", element.getNodeName(), name);
        throw new BusinessException(ErrorCode.INVALID_SSML_TAG,
            "SSML attribute not allowed: " + name);
      }
    }
    NodeList children = element.getChildNodes();
    List<Node> filtered = new ArrayList<>();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE) {
        String childName = child.getNodeName();
        if (!ALLOWED_TAGS.contains(childName)) {
          log.warn("SSML tag rejected: tag={}", childName);
          throw new BusinessException(ErrorCode.INVALID_SSML_TAG,
              "SSML tag not allowed: " + childName);
        }
        walk((Element) child, depth + 1, maxDepth);
        filtered.add(child);
      } else {
        filtered.add(child);
      }
    }
  }

  private String serializeDocument(Document doc) {
    StringBuilder sb = new StringBuilder();
    serializeNode(doc.getDocumentElement(), sb);
    return sb.toString();
  }

  private void serializeNode(Node node, StringBuilder sb) {
    if (node.getNodeType() == Node.ELEMENT_NODE) {
      sb.append('<').append(node.getNodeName());
      for (int i = 0; i < node.getAttributes().getLength(); i++) {
        Node attr = node.getAttributes().item(i);
        sb.append(' ').append(attr.getNodeName()).append("=\"")
            .append(escapeAttr(attr.getNodeValue())).append('"');
      }
      NodeList children = node.getChildNodes();
      boolean hasElementChild = false;
      for (int i = 0; i < children.getLength(); i++) {
        if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
          hasElementChild = true;
          break;
        }
      }
      if (children.getLength() == 0) {
        sb.append("/>");
      } else if (!hasElementChild) {
        sb.append('>');
        for (int i = 0; i < children.getLength(); i++) {
          Node child = children.item(i);
          if (child.getNodeType() == Node.TEXT_NODE) {
            sb.append(escapeText(child.getNodeValue()));
          }
        }
        sb.append("</").append(node.getNodeName()).append('>');
      } else {
        sb.append('>');
        for (int i = 0; i < children.getLength(); i++) {
          serializeNode(children.item(i), sb);
        }
        sb.append("</").append(node.getNodeName()).append('>');
      }
    } else if (node.getNodeType() == Node.TEXT_NODE) {
      sb.append(escapeText(node.getNodeValue()));
    }
  }

  private String escapeText(String s) {
    return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private String escapeAttr(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }

  public record SanitizedSsml(String sanitized, String rawText) {}
}
