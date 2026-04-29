package com.zyt.medconsensus.importer;

import com.zyt.medconsensus.rag.AppEmbeddingProperties;
import com.zyt.medconsensus.rag.PgVectorProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.vector-import")
public class VectorImportProperties {

    private String sourcePath = "D:/Medical_AI";
    private long maxRecords = 0;
    private boolean createIndexAfterImport = true;

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public long getMaxRecords() {
        return maxRecords;
    }

    public void setMaxRecords(long maxRecords) {
        this.maxRecords = maxRecords;
    }

    public boolean isCreateIndexAfterImport() {
        return createIndexAfterImport;
    }

    public void setCreateIndexAfterImport(boolean createIndexAfterImport) {
        this.createIndexAfterImport = createIndexAfterImport;
    }

    public int effectiveBatchSize(AppEmbeddingProperties embeddingProperties) {
        return Math.max(1, embeddingProperties.getBatchSize());
    }

    public long effectiveMaxRecords() {
        return Math.max(0, maxRecords);
    }

    public void validate(PgVectorProperties vectorProperties) {
        vectorProperties.safeDatabase();
        vectorProperties.safeTable();
    }
}
