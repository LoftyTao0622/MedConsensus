package com.zyt.medconsensus.graph;

import java.util.List;
import java.util.Map;
import org.bsc.langgraph4j.state.AgentState;

public class DiagnosisGraphState extends AgentState {

    public DiagnosisGraphState(Map<String, Object> data) {
        super(data);
    }

    public Long userId() {
        return value("userId", 0L);
    }

    public String sessionId() {
        return value("sessionId", "");
    }

    public String userMessage() {
        return value("userMessage", "");
    }

    @SuppressWarnings("unchecked")
    public List<String> memory() {
        return value("memory", List.of());
    }

    public int retryCount() {
        return value("retryCount", 0);
    }
}
