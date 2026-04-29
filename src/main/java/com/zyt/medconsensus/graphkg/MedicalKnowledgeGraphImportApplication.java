package com.zyt.medconsensus.graphkg;

import com.zyt.medconsensus.rag.PgVectorProperties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Profile;

@Profile("graph-import")
@SpringBootApplication(scanBasePackages = {
        "com.zyt.medconsensus.graphkg",
        "com.zyt.medconsensus.rag",
        "com.zyt.medconsensus.llm.validation"
})
public class MedicalKnowledgeGraphImportApplication implements ApplicationRunner {

    private final PgVectorProperties vectorProperties;
    private final GraphImportProperties importProperties;
    private final MedicalKnowledgeGraphExtractor extractor;
    private final MedicalKnowledgeGraphRepository graphRepository;

    public MedicalKnowledgeGraphImportApplication(
            PgVectorProperties vectorProperties,
            GraphImportProperties importProperties,
            MedicalKnowledgeGraphExtractor extractor,
            MedicalKnowledgeGraphRepository graphRepository
    ) {
        this.vectorProperties = vectorProperties;
        this.importProperties = importProperties;
        this.extractor = extractor;
        this.graphRepository = graphRepository;
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder(MedicalKnowledgeGraphImportApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        graphRepository.ensureSchema();
        long imported = 0;
        long lastId = 0;
        while (true) {
            List<MedicalGraphChunk> chunks = loadChunks(lastId);
            if (chunks.isEmpty()) {
                break;
            }
            for (MedicalGraphChunk chunk : chunks) {
                KnowledgeGraphExtraction extraction = extractor.extract(chunk.chunkText());
                graphRepository.saveExtraction(chunk, extraction);
                imported++;
                lastId = chunk.id();
                System.out.printf("Graph imported chunk id=%d relations=%d total=%d%n",
                        chunk.id(), extraction.getRelations().size(), imported);
                if (reachLimit(imported)) {
                    System.out.printf("Graph import finished by limit. imported=%d%n", imported);
                    return;
                }
            }
        }
        System.out.printf("Graph import finished. imported=%d%n", imported);
    }

    private List<MedicalGraphChunk> loadChunks(long lastId) throws Exception {
        String sql = """
                SELECT id, source_file, source_index, chunk_text
                FROM %s
                WHERE id > ?
                ORDER BY id
                LIMIT ?
                """.formatted(vectorProperties.safeTable());
        try (Connection connection = DriverManager.getConnection(
                vectorProperties.vectorJdbcUrl(),
                vectorProperties.getUser(),
                vectorProperties.getPassword()
        );
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, lastId);
            statement.setInt(2, importProperties.effectiveBatchSize());
            List<MedicalGraphChunk> chunks = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    chunks.add(new MedicalGraphChunk(
                            rs.getLong("id"),
                            rs.getString("source_file"),
                            rs.getLong("source_index"),
                            rs.getString("chunk_text")
                    ));
                }
            }
            return chunks;
        }
    }

    private boolean reachLimit(long imported) {
        long maxRecords = importProperties.effectiveMaxRecords();
        return maxRecords > 0 && imported >= maxRecords;
    }
}
