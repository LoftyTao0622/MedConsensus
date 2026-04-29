package com.zyt.medconsensus.importer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyt.medconsensus.rag.AppEmbeddingProperties;
import com.zyt.medconsensus.rag.EmbeddingHttpClient;
import com.zyt.medconsensus.rag.PgVectorProperties;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

@Profile("vector-import")
@SpringBootApplication(scanBasePackages = {
        "com.zyt.medconsensus.rag",
        "com.zyt.medconsensus.importer"
})
public class MedicalJsonVectorImportApplication implements ApplicationRunner {

    private final ObjectMapper objectMapper;
    private final JsonFactory jsonFactory;
    private final PgVectorProperties vectorProperties;
    private final AppEmbeddingProperties embeddingProperties;
    private final VectorImportProperties importProperties;
    private final EmbeddingHttpClient embeddingHttpClient;

    public MedicalJsonVectorImportApplication(
            ObjectMapper objectMapper,
            PgVectorProperties vectorProperties,
            AppEmbeddingProperties embeddingProperties,
            VectorImportProperties importProperties,
            EmbeddingHttpClient embeddingHttpClient
    ) {
        this.objectMapper = objectMapper;
        this.jsonFactory = objectMapper.getFactory();
        this.vectorProperties = vectorProperties;
        this.embeddingProperties = embeddingProperties;
        this.importProperties = importProperties;
        this.embeddingHttpClient = embeddingHttpClient;
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder(MedicalJsonVectorImportApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        validate();
        ensureDatabase();

        try (Connection connection = DriverManager.getConnection(
                vectorProperties.vectorJdbcUrl(),
                vectorProperties.getUser(),
                vectorProperties.getPassword()
        )) {
            connection.setAutoCommit(false);
            ensureSchema(connection);

            ImportStats stats = new ImportStats();
            List<MedicalRecordChunk> batch = new ArrayList<>(
                    importProperties.effectiveBatchSize(embeddingProperties)
            );

            for (Path file : listJsonFiles()) {
                importFile(connection, file, batch, stats);
                if (reachLimit(stats)) {
                    break;
                }
            }
            flush(connection, batch, stats);

            if (importProperties.isCreateIndexAfterImport()) {
                createVectorIndex(connection);
            }

            System.out.printf(
                    "Vector import finished. files=%d records=%d written=%d skipped=%d table=%s%n",
                    stats.files,
                    stats.records,
                    stats.written,
                    stats.skipped,
                    vectorProperties.getTable()
            );
        }
    }

    private void importFile(Connection connection, Path file, List<MedicalRecordChunk> batch, ImportStats stats) throws Exception {
        stats.files++;
        try (InputStream inputStream = Files.newInputStream(file);
             JsonParser parser = jsonFactory.createParser(inputStream)) {
            JsonToken token = parser.nextToken();
            if (token == JsonToken.START_ARRAY) {
                long index = 0;
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    enqueue(connection, file, index++, parser.readValueAsTree(), batch, stats);
                    if (reachLimit(stats)) {
                        return;
                    }
                }
                return;
            }
            if (token == JsonToken.START_OBJECT) {
                enqueue(connection, file, 0, parser.readValueAsTree(), batch, stats);
            }
        }
    }

    private void enqueue(
            Connection connection,
            Path file,
            long index,
            JsonNode node,
            List<MedicalRecordChunk> batch,
            ImportStats stats
    ) throws Exception {
        String chunkText = buildChunkText(node);
        if (!StringUtils.hasText(chunkText)) {
            stats.skipped++;
            return;
        }

        batch.add(new MedicalRecordChunk(
                normalizePath(file),
                index,
                chunkText,
                metadata(file, index, node),
                sha256(normalizePath(file) + "|" + index + "|" + chunkText)
        ));
        stats.records++;

        if (batch.size() >= importProperties.effectiveBatchSize(embeddingProperties)) {
            flush(connection, batch, stats);
        }
    }

    private void flush(Connection connection, List<MedicalRecordChunk> batch, ImportStats stats) throws Exception {
        if (batch.isEmpty()) {
            return;
        }

        List<float[]> embeddings = embeddingHttpClient.embed(batch.stream()
                .map(MedicalRecordChunk::chunkText)
                .toList());
        if (embeddings.size() != batch.size()) {
            throw new IllegalStateException("Embedding 返回数量不匹配，输入="
                    + batch.size() + "，输出=" + embeddings.size());
        }

        String sql = """
                INSERT INTO %s
                    (source_file, source_index, chunk_text, metadata, content_hash, embedding)
                VALUES
                    (?, ?, ?, ?::jsonb, ?, ?::vector)
                ON CONFLICT (content_hash) DO UPDATE SET
                    chunk_text = EXCLUDED.chunk_text,
                    metadata = EXCLUDED.metadata,
                    embedding = EXCLUDED.embedding,
                    updated_at = now()
                """.formatted(vectorProperties.safeTable());

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < batch.size(); i++) {
                MedicalRecordChunk chunk = batch.get(i);
                float[] embedding = embeddings.get(i);
                if (embedding.length != vectorProperties.getDimension()) {
                    throw new IllegalStateException("Embedding 维度不匹配，期望 "
                            + vectorProperties.getDimension() + "，实际 " + embedding.length);
                }
                statement.setString(1, chunk.sourceFile());
                statement.setLong(2, chunk.sourceIndex());
                statement.setString(3, chunk.chunkText());
                statement.setString(4, objectMapper.writeValueAsString(chunk.metadata()));
                statement.setString(5, chunk.contentHash());
                statement.setString(6, vectorLiteral(embedding));
                statement.addBatch();
            }
            int[] results = statement.executeBatch();
            connection.commit();
            stats.written += results.length;
            System.out.printf("Imported batch. records=%d written=%d%n", stats.records, stats.written);
        } catch (Exception exception) {
            connection.rollback();
            throw exception;
        } finally {
            batch.clear();
        }
    }

    private void ensureDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                vectorProperties.adminJdbcUrl(),
                vectorProperties.getUser(),
                vectorProperties.getPassword()
        );
             Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname = '" + vectorProperties.safeDatabase() + "'"
            )) {
                if (rs.next()) {
                    return;
                }
            }
            statement.execute("CREATE DATABASE " + vectorProperties.safeDatabase());
            System.out.println("Created database: " + vectorProperties.getDatabase());
        }
    }

    private void ensureSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            if (!vectorProperties.isSkipCreateVectorExtension()) {
                statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
            }
            if (vectorProperties.isDropTableFirst()) {
                statement.execute("DROP TABLE IF EXISTS " + vectorProperties.safeTable());
            }
            if (vectorProperties.isCreateTable()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS %s (
                            id BIGSERIAL PRIMARY KEY,
                            source_file TEXT NOT NULL,
                            source_index BIGINT NOT NULL,
                            chunk_text TEXT NOT NULL,
                            metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
                            content_hash TEXT NOT NULL UNIQUE,
                            embedding VECTOR(%d) NOT NULL,
                            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                            updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
                        )
                        """.formatted(vectorProperties.safeTable(), vectorProperties.getDimension()));
            }
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS %s_source_idx
                    ON %s (source_file, source_index)
                    """.formatted(vectorProperties.safeTable(), vectorProperties.safeTable()));
            connection.commit();
        }
    }

    private void createVectorIndex(Connection connection) throws Exception {
        if (!vectorProperties.isUseIndex()) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS %s_embedding_hnsw_idx
                    ON %s USING hnsw (embedding vector_cosine_ops)
                    """.formatted(vectorProperties.safeTable(), vectorProperties.safeTable()));
            connection.commit();
        }
    }

    private List<Path> listJsonFiles() throws Exception {
        Path root = Path.of(importProperties.getSourcePath());
        if (!Files.exists(root)) {
            throw new IllegalStateException("JSON 数据目录不存在：" + root);
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
    }

    private String buildChunkText(JsonNode node) {
        String instruction = node.path("instruction").asText("");
        String input = node.path("input").asText("");
        String output = node.path("output").asText("");
        String text = """
                指令：%s
                输入：%s
                回答：%s
                """.formatted(instruction, input, output).trim();
        return StringUtils.hasText(instruction + input + output) ? text : node.toString();
    }

    private Map<String, Object> metadata(Path file, long index, JsonNode node) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source_file", normalizePath(file));
        metadata.put("source_index", index);
        metadata.put("instruction", abbreviate(node.path("instruction").asText(""), 500));
        metadata.put("input", abbreviate(node.path("input").asText(""), 500));
        return metadata;
    }

    private boolean reachLimit(ImportStats stats) {
        long maxRecords = importProperties.effectiveMaxRecords();
        return maxRecords > 0 && stats.records >= maxRecords;
    }

    private void validate() {
        importProperties.validate(vectorProperties);
        if (!StringUtils.hasText(embeddingProperties.getApiKey())) {
            throw new IllegalStateException("缺少 app.embedding.api-key 或环境变量 API_KEY");
        }
    }

    private String vectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding[i]);
        }
        return builder.append(']').toString();
    }

    private String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private record MedicalRecordChunk(
            String sourceFile,
            long sourceIndex,
            String chunkText,
            Map<String, Object> metadata,
            String contentHash
    ) {
    }

    private static class ImportStats {
        long files;
        long records;
        long written;
        long skipped;
    }
}
