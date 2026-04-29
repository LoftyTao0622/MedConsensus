package com.zyt.medconsensus.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableConfigurationProperties(LangSmithProperties.class)
public class LangSmithTracingConfig {

    private static final Logger log = LoggerFactory.getLogger(LangSmithTracingConfig.class);

    @Bean
    public OpenTelemetry openTelemetry(LangSmithProperties properties) {
        String apiKey = System.getenv("LANGSMITH_API_KEY");
        if (!properties.isEnabled() || !StringUtils.hasText(apiKey) || !properties.isOtelEnabled()) {
            log.info(
                    "LangSmith OTLP tracing disabled. enabled={}, otelEnabled={}, apiKeyPresent={}. REST tracing remains available when enabled.",
                    properties.isEnabled(),
                    properties.isOtelEnabled(),
                    StringUtils.hasText(apiKey)
            );
            return OpenTelemetry.noop();
        }

        log.info(
                "LangSmith tracing enabled. endpoint={}, project={}, serviceName={}, timeout={}",
                properties.getEndpoint(),
                properties.getProject(),
                properties.getServiceName(),
                properties.getTimeout()
        );

        var exporterBuilder = OtlpHttpSpanExporter.builder()
                .setEndpoint(properties.getEndpoint())
                .setTimeout(properties.getTimeout())
                .addHeader("x-api-key", apiKey)
                .addHeader("Langsmith-Project", properties.getProject());

        if (StringUtils.hasText(properties.getWorkspaceId())) {
            exporterBuilder.addHeader("x-tenant-id", properties.getWorkspaceId());
        }
        if (StringUtils.hasText(properties.getOrganizationId())) {
            exporterBuilder.addHeader("x-organization-id", properties.getOrganizationId());
        }

        OtlpHttpSpanExporter exporter = exporterBuilder.build();

        Resource resource = Resource.getDefault().toBuilder()
                .put("service.name", properties.getServiceName())
                .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("com.zyt.medconsenus.langsmith");
    }
}
