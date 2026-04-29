package com.zyt.medconsensus.llm.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class LlmJsonValidator {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public LlmJsonValidator(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public <T> T parseAndValidate(String rawContent, Class<T> targetType) {
        try {
            T value = objectMapper.readValue(extractJson(rawContent), targetType);
            Set<ConstraintViolation<T>> violations = validator.validate(value);
            if (!violations.isEmpty()) {
                String message = violations.stream()
                        .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                        .collect(Collectors.joining("; "));
                throw new LlmOutputValidationException("LLM output validation failed: " + message);
            }
            return value;
        } catch (LlmOutputValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new LlmOutputValidationException("LLM output is not valid JSON for "
                    + targetType.getSimpleName(), exception);
        }
    }

    private String extractJson(String rawContent) {
        if (rawContent == null) {
            throw new LlmOutputValidationException("LLM output is empty");
        }

        String content = rawContent.trim();
        if (content.startsWith("```")) {
            int firstLineBreak = content.indexOf('\n');
            if (firstLineBreak >= 0) {
                content = content.substring(firstLineBreak + 1);
            }
            int lastFence = content.lastIndexOf("```");
            if (lastFence >= 0) {
                content = content.substring(0, lastFence);
            }
        }

        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1).trim();
        }
        return content;
    }
}
