package com.zyt.medconsensus.observability;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "langsmith")
public class LangSmithProperties {

    private boolean enabled = false;

    private String endpoint = "https://api.smith.langchain.com/otel/v1/traces";

    private String apiEndpoint = "https://api.smith.langchain.com/api/v1";

    private String project = "MedConsenus";

    private String workspaceId = "";

    private String organizationId = "";

    private String serviceName = "medconsenus-backend";

    private boolean captureContent = false;

    private Duration timeout = Duration.ofSeconds(60);

    private boolean otelEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getApiEndpoint() {
        return apiEndpoint;
    }

    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public boolean isCaptureContent() {
        return captureContent;
    }

    public void setCaptureContent(boolean captureContent) {
        this.captureContent = captureContent;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public boolean isOtelEnabled() {
        return otelEnabled;
    }

    public void setOtelEnabled(boolean otelEnabled) {
        this.otelEnabled = otelEnabled;
    }
}
