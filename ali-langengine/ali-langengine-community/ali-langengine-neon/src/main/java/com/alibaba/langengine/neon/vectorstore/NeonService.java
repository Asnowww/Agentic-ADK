/**
 * Copyright (C) 2024 AIDC-AI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.langengine.neon.vectorstore;

import com.alibaba.langengine.core.indexes.Document;
import com.pgvector.PGvector;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.*;
import java.util.*;


@Slf4j
@Data
public class NeonService {

    private String url;
    private String username;
    private String password;
    private String tableName;
    private NeonParam neonParam;
    private Connection connection;

    public NeonService(String url, String username, String password, String tableName, NeonParam neonParam) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.tableName = tableName;
        this.neonParam = neonParam != null ? neonParam : new NeonParam();
    }

    public void init() {
        try {
            // Load PostgreSQL driver
            Class.forName("org.postgresql.Driver");
            
            // Create connection
            connection = DriverManager.getConnection(url, username, password);
            
            // Create pgvector extension if not exists
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE EXTENSION IF NOT EXISTS vector");
            }
            
            // Create table if not exists
            createTableIfNotExists();
            
            log.info("Successfully initialized Neon table: {}", tableName);
        } catch (Exception e) {
            log.error("Neon Service init failed", e);
            throw new RuntimeException("Failed to initialize Neon service", e);
        }
    }

    private void createTableIfNotExists() throws SQLException {
        String contentField = neonParam.getFieldNamePageContent();
        String idField = neonParam.getFieldNameUniqueId();
        String metaField = neonParam.getFieldMeta();
        int dimension = neonParam.getInitParam().getDimension();

        String createTableSQL = String.format(
            "CREATE TABLE IF NOT EXISTS %s (" +
            "id SERIAL PRIMARY KEY, " +
            "%s TEXT, " +
            "%s TEXT, " +
            "%s JSONB, " +
            "embedding vector(%d)" +
            ")",
            tableName, idField, contentField, metaField, dimension
        );

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
            
            // Create vector index
            String indexName = tableName + "_embedding_idx";
            String indexType = neonParam.getInitParam().getIndexType();
            String createIndexSQL = String.format(
                "CREATE INDEX IF NOT EXISTS %s ON %s USING %s (embedding vector_cosine_ops)",
                indexName, tableName, indexType
            );
            stmt.execute(createIndexSQL);
        }
    }

    public void addDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        String contentField = neonParam.getFieldNamePageContent();
        String idField = neonParam.getFieldNameUniqueId();
        String metaField = neonParam.getFieldMeta();

        String insertSQL = String.format(
            "INSERT INTO %s (%s, %s, %s, embedding) VALUES (?, ?, ?::jsonb, ?)",
            tableName, idField, contentField, metaField
        );

        try (PreparedStatement pstmt = connection.prepareStatement(insertSQL)) {
            for (Document doc : documents) {
                if (doc.getEmbedding() == null || doc.getEmbedding().isEmpty()) {
                    continue;
                }

                // Convert embedding to float array
                float[] embedding = new float[doc.getEmbedding().size()];
                for (int i = 0; i < doc.getEmbedding().size(); i++) {
                    embedding[i] = doc.getEmbedding().get(i).floatValue();
                }

                pstmt.setString(1, doc.getUniqueId());
                pstmt.setString(2, doc.getPageContent());
                pstmt.setString(3, doc.getMetadata() != null ? 
                    com.alibaba.fastjson.JSON.toJSONString(doc.getMetadata()) : "{}");
                pstmt.setObject(4, new PGvector(embedding));

                pstmt.addBatch();
            }
            pstmt.executeBatch();
        } catch (SQLException e) {
            log.error("Failed to add documents to Neon", e);
            throw new RuntimeException("Failed to add documents", e);
        }
    }

    public List<Document> similaritySearch(List<Float> query, int k, Double maxDistanceValue, Integer type) {
        if (query == null || query.isEmpty()) {
            return Collections.emptyList();
        }

        String contentField = neonParam.getFieldNamePageContent();
        String idField = neonParam.getFieldNameUniqueId();
        String metaField = neonParam.getFieldMeta();
        String distance = neonParam.getInitParam().getVectorDistance();

        // Convert query to float array
        float[] queryVector = new float[query.size()];
        for (int i = 0; i < query.size(); i++) {
            queryVector[i] = query.get(i);
        }

        String distanceFunc = getDistanceFunction(distance);
        String selectSQL = String.format(
            "SELECT %s, %s, %s, embedding %s ? AS distance " +
            "FROM %s " +
            "ORDER BY embedding %s ? " +
            "LIMIT ?",
            idField, contentField, metaField, distanceFunc,
            tableName, getDistanceOperator(distance)
        );

        List<Document> results = new ArrayList<>();
        try (PreparedStatement pstmt = connection.prepareStatement(selectSQL)) {
            pstmt.setObject(1, new PGvector(queryVector));
            pstmt.setObject(2, new PGvector(queryVector));
            pstmt.setInt(3, k);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    double dist = rs.getDouble("distance");
                    
                    // Apply distance filter if specified
                    if (maxDistanceValue != null && dist > maxDistanceValue) {
                        continue;
                    }

                    Document doc = new Document();
                    doc.setUniqueId(rs.getString(idField));
                    doc.setPageContent(rs.getString(contentField));
                    
                    String metaJson = rs.getString(metaField);
                    if (StringUtils.isNotBlank(metaJson)) {
                        Map<String, Object> metadata = com.alibaba.fastjson.JSON.parseObject(metaJson, Map.class);
                        doc.setMetadata(metadata);
                    }
                    
                    doc.setScore(dist);
                    results.add(doc);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to search similar documents", e);
            throw new RuntimeException("Failed to search documents", e);
        }

        return results;
    }

    private String getDistanceFunction(String distance) {
        switch (distance.toLowerCase()) {
            case "cosine":
                return "<->";
            case "l2":
                return "<->";
            case "inner_product":
                return "<#>";
            default:
                return "<->";
        }
    }

    private String getDistanceOperator(String distance) {
        switch (distance.toLowerCase()) {
            case "cosine":
                return "<->";
            case "l2":
                return "<->";
            case "inner_product":
                return "<#>";
            default:
                return "<->";
        }
    }

    public void dropTable() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + tableName);
            log.info("Dropped Neon table: {}", tableName);
        } catch (SQLException e) {
            log.error("Failed to drop table", e);
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                log.error("Failed to close connection", e);
            }
        }
    }
}
