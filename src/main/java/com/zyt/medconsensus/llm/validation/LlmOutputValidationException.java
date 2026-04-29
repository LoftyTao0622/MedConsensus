package com.zyt.medconsensus.llm.validation;

public class LlmOutputValidationException extends RuntimeException {

    public LlmOutputValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public LlmOutputValidationException(String message) {
        super(message);
    }
}
