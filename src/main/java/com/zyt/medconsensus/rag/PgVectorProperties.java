package com.zyt.medconsensus.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.vector-store.pgvector")
public class PgVectorProperties {

    private String host = "127.0.0.1";
    private int port = 5432;
    private String user = "postgres";
    private String password = "123456";
    private String database = "vector_db";
    private String table = "medical_embedding";
    private int dimension = 1024;
    private boolean createTable = true;
    private boolean dropTableFirst = false;
    private boolean useIndex = true;
    private boolean skipCreateVectorExtension = false;
    private int maxResults = 5;
    private double minScore = 0.6;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public boolean isCreateTable() {
        return createTable;
    }

    public void setCreateTable(boolean createTable) {
        this.createTable = createTable;
    }

    public boolean isDropTableFirst() {
        return dropTableFirst;
    }

    public void setDropTableFirst(boolean dropTableFirst) {
        this.dropTableFirst = dropTableFirst;
    }

    public boolean isUseIndex() {
        return useIndex;
    }

    public void setUseIndex(boolean useIndex) {
        this.useIndex = useIndex;
    }

    public boolean isSkipCreateVectorExtension() {
        return skipCreateVectorExtension;
    }

    public void setSkipCreateVectorExtension(boolean skipCreateVectorExtension) {
        this.skipCreateVectorExtension = skipCreateVectorExtension;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public String adminJdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/postgres";
    }

    public String vectorJdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }

    public String safeDatabase() {
        return safeIdentifier(database);
    }

    public String safeTable() {
        return safeIdentifier(table);
    }

    private String safeIdentifier(String value) {
        if (value != null && value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return value;
        }
        throw new IllegalArgumentException("Invalid PostgreSQL identifier: " + value);
    }
}
