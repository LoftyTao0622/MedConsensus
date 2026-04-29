package com.zyt.medconsensus.graphkg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Query;
import org.neo4j.driver.SessionConfig;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class MedicalKnowledgeGraphRepository {

    private final Driver driver;
    private final Neo4jProperties properties;

    public MedicalKnowledgeGraphRepository(Driver driver, Neo4jProperties properties) {
        this.driver = driver;
        this.properties = properties;
    }

    public void ensureSchema() {
        try (var session = driver.session(sessionConfig())) {
            session.executeWrite(tx -> {
                tx.run("CREATE CONSTRAINT symptom_name IF NOT EXISTS FOR (n:Symptom) REQUIRE n.name IS UNIQUE");
                tx.run("CREATE CONSTRAINT disease_name IF NOT EXISTS FOR (n:Disease) REQUIRE n.name IS UNIQUE");
                tx.run("CREATE CONSTRAINT treatment_name IF NOT EXISTS FOR (n:Treatment) REQUIRE n.name IS UNIQUE");
                tx.run("CREATE CONSTRAINT examination_name IF NOT EXISTS FOR (n:Examination) REQUIRE n.name IS UNIQUE");
                tx.run("CREATE CONSTRAINT department_name IF NOT EXISTS FOR (n:Department) REQUIRE n.name IS UNIQUE");
                tx.run("CREATE CONSTRAINT risk_factor_name IF NOT EXISTS FOR (n:RiskFactor) REQUIRE n.name IS UNIQUE");
                tx.run("CREATE CONSTRAINT diet_name IF NOT EXISTS FOR (n:Diet) REQUIRE n.name IS UNIQUE");
                tx.run("CREATE CONSTRAINT exercise_name IF NOT EXISTS FOR (n:Exercise) REQUIRE n.name IS UNIQUE");
                return null;
            });
        }
    }

    public void saveExtraction(MedicalGraphChunk chunk, KnowledgeGraphExtraction extraction) {
        if (extraction == null || extraction.getRelations().isEmpty()) {
            return;
        }
        try (var session = driver.session(sessionConfig())) {
            session.executeWrite(tx -> {
                for (KnowledgeGraphExtraction.Relation relation : extraction.getRelations()) {
                    String sourceLabel = safeLabel(relation.getSourceType());
                    String targetLabel = safeLabel(relation.getTargetType());
                    String relationshipType = safeRelationship(relation.getRelation());
                    if (!StringUtils.hasText(sourceLabel)
                            || !StringUtils.hasText(targetLabel)
                            || !StringUtils.hasText(relationshipType)
                            || !StringUtils.hasText(relation.getSource())
                            || !StringUtils.hasText(relation.getTarget())) {
                        continue;
                    }
                    String cypher = """
                            MERGE (s:%s {name: $source})
                            MERGE (t:%s {name: $target})
                            MERGE (s)-[r:%s]->(t)
                            SET r.confidence = CASE
                                  WHEN r.confidence IS NULL OR r.confidence < $confidence
                                  THEN $confidence ELSE r.confidence END,
                                r.sources = CASE
                                  WHEN r.sources IS NULL THEN [$sourceKey]
                                  WHEN NOT $sourceKey IN r.sources THEN r.sources + $sourceKey
                                  ELSE r.sources END,
                                r.updatedAt = datetime()
                            """.formatted(sourceLabel, targetLabel, relationshipType);
                    tx.run(new Query(cypher, Map.of(
                            "source", normalizeName(relation.getSource()),
                            "target", normalizeName(relation.getTarget()),
                            "confidence", relation.getConfidence(),
                            "sourceKey", chunk.sourceFile() + "#" + chunk.sourceIndex()
                    )));
                }
                return null;
            });
        }
    }

    public List<MedicalGraphPath> findSymptomDiseaseTreatmentPaths(List<String> symptoms, int limit) {
        List<String> normalizedSymptoms = symptoms.stream()
                .filter(StringUtils::hasText)
                .map(this::normalizeName)
                .distinct()
                .toList();
        if (normalizedSymptoms.isEmpty()) {
            return List.of();
        }

        String cypher = """
                MATCH (s:Symptom)-[r:SUGGESTS|HAS_SYMPTOM]-(d:Disease)
                WHERE any(term IN $symptoms WHERE s.name CONTAINS term
                    OR term CONTAINS s.name
                    OR d.name CONTAINS term
                    OR term CONTAINS d.name)
                OPTIONAL MATCH (d)-[:TREATED_BY]->(t:Treatment)
                OPTIONAL MATCH (d)-[:CHECKED_BY]->(e:Examination)
                RETURN s.name AS symptom,
                       d.name AS disease,
                       collect(DISTINCT t.name)[0..5] AS treatments,
                       collect(DISTINCT e.name)[0..5] AS examinations,
                       max(coalesce(r.confidence, 0.5)) AS confidence
                ORDER BY confidence DESC, disease ASC
                LIMIT $limit
                """;

        try (var session = driver.session(sessionConfig())) {
            return session.executeRead(tx -> {
                var result = tx.run(new Query(cypher, Map.of(
                        "symptoms", normalizedSymptoms,
                        "limit", Math.max(1, limit)
                )));
                List<MedicalGraphPath> paths = new ArrayList<>();
                while (result.hasNext()) {
                    var record = result.next();
                    paths.add(new MedicalGraphPath(
                            record.get("symptom").asString(""),
                            record.get("disease").asString(""),
                            record.get("treatments").asList(value -> value.asString("")),
                            record.get("examinations").asList(value -> value.asString("")),
                            record.get("confidence").asDouble(0.5)
                    ));
                }
                return paths;
            });
        }
    }

    private SessionConfig sessionConfig() {
        return SessionConfig.builder()
                .withDatabase(properties.getDatabase())
                .build();
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeLabel(String value) {
        return switch (normalizeToken(value)) {
            case "SYMPTOM" -> "Symptom";
            case "DISEASE" -> "Disease";
            case "TREATMENT" -> "Treatment";
            case "EXAMINATION" -> "Examination";
            case "DEPARTMENT" -> "Department";
            case "RISKFACTOR", "RISK_FACTOR" -> "RiskFactor";
            case "DIET" -> "Diet";
            case "EXERCISE" -> "Exercise";
            default -> "";
        };
    }

    private String safeRelationship(String value) {
        return switch (normalizeToken(value)) {
            case "SUGGESTS" -> "SUGGESTS";
            case "HAS_SYMPTOM" -> "HAS_SYMPTOM";
            case "TREATED_BY" -> "TREATED_BY";
            case "CHECKED_BY" -> "CHECKED_BY";
            case "BELONGS_TO" -> "BELONGS_TO";
            case "INCREASES_RISK" -> "INCREASES_RISK";
            case "HELPS_TREAT" -> "HELPS_TREAT";
            default -> "";
        };
    }

    private String normalizeToken(String value) {
        return value == null ? "" : value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }
}
