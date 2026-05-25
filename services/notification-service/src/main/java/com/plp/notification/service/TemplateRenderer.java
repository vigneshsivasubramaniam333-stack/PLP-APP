package com.plp.notification.service;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class TemplateRenderer {

    public String render(String template, Map<String, String> variables) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }
}
