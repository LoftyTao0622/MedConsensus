package com.zyt.medconsensus.observability;

import com.zyt.medconsensus.graph.DiagnosisGraphState;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LangSmithTracingService {

    private final Tracer tracer;
    private final LangSmithProperties properties;
    private final LangSmithRestRunClient restRunClient;
    private final ThreadLocal<UUID> currentRunId = new ThreadLocal<>();

    public LangSmithTracingService(Tracer tracer, LangSmithProperties properties, LangSmithRestRunClient restRunClient) {
        this.tracer = tracer;
        this.properties = properties;
        this.restRunClient = restRunClient;
    }

    public <T> T traceWorkflow(String workflowName, Long userId, String sessionId, String userMessage, Supplier<T> action) {
        return traceSpan(
                workflowName,
                SpanKind.INTERNAL,
                "chain",
                sessionId,
                Map.of("input", properties.isCaptureContent() ? safePreview(userMessage) : "content redacted"),
                Map.of(
                        "user_id", String.valueOf(userId),
                        "session_id", StringUtils.hasText(sessionId) ? sessionId : "",
                        "input_length", safeLength(userMessage)
                ),
                span -> {
                    span.setAttribute("langsmith.trace.name", workflowName);
                    if (StringUtils.hasText(sessionId)) {
                        span.setAttribute("langsmith.trace.session_name", sessionId);
                    }
                    span.setAttribute("langsmith.metadata.user_id", String.valueOf(userId));
                    span.setAttribute("langsmith.metadata.input_length", safeLength(userMessage));
                    setInputValue(span, userMessage);
                },
                action
        );
    }

    public <T> T traceNode(String nodeName, DiagnosisGraphState state, Supplier<T> action) {
        return traceSpan(
                "workflow." + nodeName,
                SpanKind.INTERNAL,
                "chain",
                state.sessionId(),
                Map.of("input", properties.isCaptureContent() ? safePreview(state.userMessage()) : "content redacted"),
                Map.of(
                        "node", nodeName,
                        "retry_count", state.retryCount(),
                        "memory_size", state.memory().size()
                ),
                span -> {
                    span.setAttribute("langsmith.metadata.node", nodeName);
                    span.setAttribute("langsmith.metadata.retry_count", state.retryCount());
                    span.setAttribute("langsmith.metadata.memory_size", state.memory().size());
                    setInputValue(span, state.userMessage());
                },
                action
        );
    }

    public String traceModelCall(
            String modelName,
            String systemPrompt,
            List<Map<String, Object>> messages,
            Supplier<String> action
    ) {
        Span span = tracer.spanBuilder("llm." + modelName)
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();
        UUID parentRunId = currentRunId.get();
        UUID runId = restRunClient.startRun(
                "llm." + modelName,
                "llm",
                parentRunId,
                Map.of("messages", properties.isCaptureContent() ? joinMessages(messages) : "content redacted"),
                Map.of("model", modelName, "message_count", messages.size())
        );

        try (Scope ignored = span.makeCurrent()) {
            span.setAttribute("langsmith.span.kind", "llm");
            span.setAttribute("gen_ai.system", "openai");
            span.setAttribute("gen_ai.operation.name", "chat");
            span.setAttribute("gen_ai.request.model", modelName);
            span.setAttribute("llm.request.type", "chat");
            span.setAttribute("langsmith.metadata.message_count", messages.size());
            span.setAttribute("langsmith.metadata.system_prompt_length", safeLength(systemPrompt));
            setInputAttributes(span, systemPrompt, messages);

            String result = action.get();

            span.setAttribute("gen_ai.response.model", modelName);
            setOutputAttributes(span, result);
            span.setStatus(StatusCode.OK);
            restRunClient.endRun(runId, Map.of("response", properties.isCaptureContent() ? safePreview(result) : "content redacted"), null);
            return result;
        } catch (RuntimeException exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, exception.getMessage());
            restRunClient.endRun(runId, Map.of(), exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    private <T> T traceSpan(
            String spanName,
            SpanKind spanKind,
            String runType,
            String sessionId,
            Map<String, Object> inputs,
            Map<String, Object> metadata,
            java.util.function.Consumer<Span> decorator,
            Supplier<T> action
    ) {
        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(spanKind)
                .startSpan();
        UUID parentRunId = currentRunId.get();
        UUID runId = restRunClient.startRun(spanName, runType, parentRunId, inputs, metadata);
        currentRunId.set(runId == null ? parentRunId : runId);

        try (Scope ignored = span.makeCurrent()) {
            span.setAttribute("langsmith.span.kind", runType);
            if (StringUtils.hasText(sessionId)) {
                span.setAttribute("langsmith.trace.session_id", sessionId);
            }
            decorator.accept(span);
            T result = action.get();
            setOutputValue(span, result);
            span.setStatus(StatusCode.OK);
            restRunClient.endRun(runId, Map.of("result", properties.isCaptureContent() ? safePreview(String.valueOf(result)) : "content redacted"), null);
            return result;
        } catch (RuntimeException exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, exception.getMessage());
            restRunClient.endRun(runId, Map.of(), exception);
            throw exception;
        } finally {
            if (parentRunId == null) {
                currentRunId.remove();
            } else {
                currentRunId.set(parentRunId);
            }
            span.end();
        }
    }

    private long safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private String joinMessages(List<Map<String, Object>> messages) {
        return messages.stream()
                .map(item -> item.getOrDefault("role", "unknown") + ": " + item.getOrDefault("MedContent", ""))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private void setInputAttributes(Span span, String systemPrompt, List<Map<String, Object>> messages) {
        if (!properties.isCaptureContent()) {
            setInputValue(span, "content redacted");
            return;
        }

        if (StringUtils.hasText(systemPrompt)) {
            span.setAttribute("gen_ai.prompt.0.role", "system");
            span.setAttribute("gen_ai.prompt.0.content", systemPrompt);
        }

        int offset = StringUtils.hasText(systemPrompt) ? 1 : 0;
        for (int index = 0; index < messages.size(); index++) {
            Map<String, Object> message = messages.get(index);
            int attributeIndex = index + offset;
            span.setAttribute("gen_ai.prompt." + attributeIndex + ".role", String.valueOf(message.getOrDefault("role", "user")));
            span.setAttribute("gen_ai.prompt." + attributeIndex + ".content", String.valueOf(message.getOrDefault("MedContent", "")));
        }
    }

    private void setOutputAttributes(Span span, String result) {
        if (!properties.isCaptureContent()) {
            setOutputValue(span, "content redacted");
            return;
        }

        span.setAttribute("gen_ai.completion.0.role", "assistant");
        span.setAttribute("gen_ai.completion.0.content", result == null ? "" : result);
        setOutputValue(span, result);
    }

    private void setInputValue(Span span, String value) {
        span.setAttribute("input.value", properties.isCaptureContent() ? safePreview(value) : "content redacted");
    }

    private void setOutputValue(Span span, Object value) {
        if (!properties.isCaptureContent()) {
            span.setAttribute("output.value", "content redacted");
            return;
        }
        span.setAttribute("output.value", safePreview(value == null ? "" : String.valueOf(value)));
    }

    private String safePreview(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.replaceAll("\\s+", " ").trim();
        return trimmed.length() <= 240 ? trimmed : trimmed.substring(0, 240) + "...";
    }
}
