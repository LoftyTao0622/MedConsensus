package com.zyt.medconsensus.graphkg;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.graph-import")
public class GraphImportProperties {

    private int batchSize = 20;
    private long maxRecords = 0;
    private String sourceFile = "";
    private String llmModel = "deepseek-v4-flash";
    private double llmTemperature = 0.1;
    private String llmBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String llmApiKey = "";

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getMaxRecords() {
        return maxRecords;
    }

    public void setMaxRecords(long maxRecords) {
        this.maxRecords = maxRecords;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public double getLlmTemperature() {
        return llmTemperature;
    }

    public void setLlmTemperature(double llmTemperature) {
        this.llmTemperature = llmTemperature;
    }

    public String getLlmBaseUrl() {
        return llmBaseUrl;
    }

    public void setLlmBaseUrl(String llmBaseUrl) {
        this.llmBaseUrl = llmBaseUrl;
    }

    public String getLlmApiKey() {
        return llmApiKey;
    }

    public void setLlmApiKey(String llmApiKey) {
        this.llmApiKey = llmApiKey;
    }

    public int effectiveBatchSize() {
        return Math.max(1, batchSize);
    }

    public long effectiveMaxRecords() {
        return Math.max(0, maxRecords);
    }
}
