package com.zyt.medconsensus.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "spring.ai.openai")
public class AiWorkflowProperties {

    private String apiKey;
    private String baseUrl;
    private Chat chat = new Chat();
    private Collector collector = new Collector();
    private Reviewers reviewers = new Reviewers();
    private Decision decision = new Decision();
    private Treatment treatment = new Treatment();

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Chat getChat() {
        return chat;
    }

    public void setChat(Chat chat) {
        this.chat = chat;
    }

    public Collector getCollector() {
        return collector;
    }

    public void setCollector(Collector collector) {
        this.collector = collector;
    }

    public Reviewers getReviewers() {
        return reviewers;
    }

    public void setReviewers(Reviewers reviewers) {
        this.reviewers = reviewers;
    }

    public Decision getDecision() {
        return decision;
    }

    public void setDecision(Decision decision) {
        this.decision = decision;
    }

    public Treatment getTreatment() {
        return treatment;
    }

    public void setTreatment(Treatment treatment) {
        this.treatment = treatment;
    }

    public static class Chat {
        private Options options = new Options();

        public Options getOptions() {
            return options;
        }

        public void setOptions(Options options) {
            this.options = options;
        }
    }

    public static class Options {
        private String model;
        private double temperature = 0.2;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }
    }

    public static class Collector {
        private String apiKey;
        private String baseUrl;
        private String model;
        private double temperature = 0.3;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }
    }

    public static class Reviewers {
        private Reviewer gpt = new Reviewer();
        private Reviewer kimi = new Reviewer();
        private Reviewer glm = new Reviewer();

        public Reviewer getGpt() {
            return gpt;
        }

        public void setGpt(Reviewer gpt) {
            this.gpt = gpt;
        }

        public Reviewer getKimi() {
            return kimi;
        }

        public void setKimi(Reviewer kimi) {
            this.kimi = kimi;
        }

        public Reviewer getGlm() {
            return glm;
        }

        public void setGlm(Reviewer glm) {
            this.glm = glm;
        }
    }

    public static class Reviewer {
        private double weight;
        private String apiKey;
        private String baseUrl;
        private String model;
        private double temperature = 0.2;

        public double getWeight() {
            return weight;
        }

        public void setWeight(double weight) {
            this.weight = weight;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }
    }

    public static class Decision {
        private String apiKey;
        private String baseUrl;
        private String model;
        private double temperature = 0.1;
        private double confidenceThreshold = 0.75;
        private double highRiskThreshold = 0.85;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public double getConfidenceThreshold() {
            return confidenceThreshold;
        }

        public void setConfidenceThreshold(double confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold;
        }

        public double getHighRiskThreshold() {
            return highRiskThreshold;
        }

        public void setHighRiskThreshold(double highRiskThreshold) {
            this.highRiskThreshold = highRiskThreshold;
        }
    }

    public static class Treatment {
        private String apiKey;
        private String baseUrl;
        private String model;
        private double temperature = 0.2;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }
    }
}
