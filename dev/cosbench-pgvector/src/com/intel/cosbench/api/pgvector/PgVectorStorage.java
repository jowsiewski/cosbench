/**

Copyright 2024 Intel Corporation, All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.intel.cosbench.api.pgvector;

import static com.intel.cosbench.client.pgvector.PgVectorConstants.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.intel.cosbench.api.storage.NoneStorage;
import com.intel.cosbench.api.storage.StorageException;
import com.intel.cosbench.config.Config;
import com.intel.cosbench.log.Logger;

/**
 * COSBench StorageAPI implementation for pgvector (PostgreSQL vector extension).
 *
 * <p>COSBench operation mapping:
 * <ul>
 *   <li>init        — CREATE TABLE + CREATE INDEX (hnsw or ivfflat)
 *   <li>write       — INSERT INTO table (id, embedding) VALUES (?, ?::vector)
 *   <li>read        — SELECT id FROM table ORDER BY embedding &lt;op&gt; ?::vector LIMIT k
 *   <li>cleanup     — DELETE FROM table WHERE id = ?
 *   <li>dispose     — DROP TABLE IF EXISTS
 * </ul>
 *
 * <p>One table per COSBench container. Table name = container name.
 * Vectors are generated synthetically from a seeded PRNG (no external data needed).
 */
class PgVectorStorage extends NoneStorage {

    private String host;
    private int    port;
    private String database;
    private String username;
    private String password;
    private boolean ssl;
    private int    timeout;
    private int    poolSize;

    private String schema;
    private String vectorColumn;
    private String idColumn;
    private int    dim;
    private String indexType;
    private String distanceMetric;
    private int    hnswM;
    private int    hnswEfConstruction;
    private int    ivfflatLists;
    private int    probes;
    private int    knn;
    private long   randomSeed;

    private String distanceOperator;
    private String indexOps;

    private BlockingQueue<Connection> pool;

    public PgVectorStorage() {
    }

    @Override
    public void init(Config config, Logger logger) {
        super.init(config, logger);

        host     = config.get(HOST_KEY,     HOST_DEFAULT);
        port     = config.getInt(PORT_KEY,  PORT_DEFAULT);
        database = config.get(DATABASE_KEY, DATABASE_DEFAULT);
        username = config.get(USERNAME_KEY, USERNAME_DEFAULT);
        password = config.get(PASSWORD_KEY, PASSWORD_DEFAULT);
        ssl      = config.getBoolean(SSL_KEY, SSL_DEFAULT);
        timeout  = config.getInt(TIMEOUT_KEY, TIMEOUT_DEFAULT);
        poolSize = config.getInt(POOL_SIZE_KEY, POOL_SIZE_DEFAULT);

        schema        = config.get(SCHEMA_KEY,        SCHEMA_DEFAULT);
        vectorColumn  = config.get(VECTOR_COLUMN_KEY, VECTOR_COLUMN_DEFAULT);
        idColumn      = config.get(ID_COLUMN_KEY,     ID_COLUMN_DEFAULT);
        dim           = config.getInt(DIM_KEY,         DIM_DEFAULT);
        indexType     = config.get(INDEX_TYPE_KEY,     INDEX_TYPE_DEFAULT).toLowerCase();
        distanceMetric = config.get(DISTANCE_METRIC_KEY, DISTANCE_METRIC_DEFAULT).toLowerCase();
        hnswM              = config.getInt(HNSW_M_KEY,              HNSW_M_DEFAULT);
        hnswEfConstruction = config.getInt(HNSW_EF_CONSTRUCTION_KEY, HNSW_EF_CONSTRUCTION_DEFAULT);
        ivfflatLists       = config.getInt(IVFFLAT_LISTS_KEY,        IVFFLAT_LISTS_DEFAULT);
        probes             = config.getInt(PROBES_KEY,               PROBES_DEFAULT);
        knn                = config.getInt(KNN_KEY,                  KNN_DEFAULT);
        randomSeed         = config.getLong(RANDOM_SEED_KEY,         RANDOM_SEED_DEFAULT);

        distanceOperator = resolveOperator(distanceMetric);
        indexOps         = resolveIndexOps(distanceMetric);

        parms.put(HOST_KEY,     host);
        parms.put(PORT_KEY,     port);
        parms.put(DATABASE_KEY, database);
        parms.put(USERNAME_KEY, username);
        parms.put(SSL_KEY,      ssl);
        parms.put(DIM_KEY,      dim);
        parms.put(INDEX_TYPE_KEY,      indexType);
        parms.put(DISTANCE_METRIC_KEY, distanceMetric);
        parms.put(KNN_KEY,      knn);

        logger.debug("pgvector storage config: {}", parms);

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new StorageException("PostgreSQL JDBC driver not found", e);
        }

        pool = new ArrayBlockingQueue<Connection>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            pool.offer(openConnection());
        }

        logger.debug("pgvector connection pool ready ({}:{}/{}  poolSize={})", host, port);
    }

    @Override
    public void dispose() {
        super.dispose();
        if (pool != null) {
            for (Connection c : pool) {
                closeQuietly(c);
            }
            pool = null;
        }
    }

    /** createContainer → CREATE TABLE + CREATE INDEX */
    @Override
    public void createContainer(String container, Config config) {
        super.createContainer(container, config);
        String table = qualifiedTable(container);
        Connection c = acquire();
        try {
            try (Statement st = c.createStatement()) {
                st.execute("CREATE EXTENSION IF NOT EXISTS vector");
                st.execute(
                    "CREATE TABLE IF NOT EXISTS " + table + " (" +
                    idColumn + " TEXT PRIMARY KEY, " +
                    vectorColumn + " vector(" + dim + ")" +
                    ")"
                );
                createIndex(st, table, container);
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        } finally {
            release(c);
        }
    }

    /** deleteContainer → DROP TABLE IF EXISTS */
    @Override
    public void deleteContainer(String container, Config config) {
        super.deleteContainer(container, config);
        String table = qualifiedTable(container);
        Connection c = acquire();
        try {
            try (Statement st = c.createStatement()) {
                st.execute("DROP TABLE IF EXISTS " + table);
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        } finally {
            release(c);
        }
    }

    /** createObject → INSERT (upsert) vector row */
    @Override
    public void createObject(String container, String object,
            InputStream data, long length, Config config) {
        super.createObject(container, object, data, length, config);
        String table = qualifiedTable(container);
        String sql = "INSERT INTO " + table + " (" + idColumn + ", " + vectorColumn + ")" +
                     " VALUES (?, ?::vector)" +
                     " ON CONFLICT (" + idColumn + ") DO UPDATE SET " +
                     vectorColumn + " = EXCLUDED." + vectorColumn;
        Connection c = acquire();
        try {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, object);
                ps.setString(2, vectorToSql(generateVector(object)));
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        } finally {
            release(c);
        }
    }

    /** getObject → KNN search ORDER BY embedding <op> ?::vector LIMIT k */
    @Override
    public InputStream getObject(String container, String object, Config config) {
        super.getObject(container, object, config);
        String table = qualifiedTable(container);
        String sql = "SELECT " + idColumn + " FROM " + table +
                     " ORDER BY " + vectorColumn + " " + distanceOperator + " ?::vector LIMIT ?";
        Connection c = acquire();
        try {
            setProbes(c);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, vectorToSql(generateVector(object)));
                ps.setInt(2, knn);
                ResultSet rs = ps.executeQuery();
                StringBuilder sb = new StringBuilder();
                while (rs.next()) {
                    sb.append(rs.getString(1)).append('\n');
                }
                return new ByteArrayInputStream(sb.toString().getBytes("UTF-8"));
            }
        } catch (Exception e) {
            throw new StorageException(e);
        } finally {
            release(c);
        }
    }

    /** deleteObject → DELETE FROM table WHERE id = ? */
    @Override
    public void deleteObject(String container, String object, Config config) {
        super.deleteObject(container, object, config);
        String table = qualifiedTable(container);
        String sql = "DELETE FROM " + table + " WHERE " + idColumn + " = ?";
        Connection c = acquire();
        try {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, object);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        } finally {
            release(c);
        }
    }

    /** getList → SELECT id FROM table (full scan) */
    @Override
    public InputStream getList(String container, String object, Config config) {
        super.getList(container, object, config);
        String table = qualifiedTable(container);
        String sql = "SELECT " + idColumn + " FROM " + table;
        Connection c = acquire();
        try {
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                StringBuilder sb = new StringBuilder();
                while (rs.next()) {
                    sb.append(rs.getString(1)).append('\n');
                }
                return new ByteArrayInputStream(sb.toString().getBytes("UTF-8"));
            }
        } catch (Exception e) {
            throw new StorageException(e);
        } finally {
            release(c);
        }
    }

    // --------------------------------------------------------------------------
    // Private helpers
    // --------------------------------------------------------------------------

    private Connection openConnection() {
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        Properties props = new Properties();
        props.setProperty("user",     username);
        props.setProperty("password", password);
        props.setProperty("ssl",      Boolean.toString(ssl));
        props.setProperty("loginTimeout",  String.valueOf(timeout));
        props.setProperty("socketTimeout", String.valueOf(timeout));
        try {
            return DriverManager.getConnection(url, props);
        } catch (SQLException e) {
            throw new StorageException("Cannot connect to PostgreSQL at " + url, e);
        }
    }

    private Connection acquire() {
        Connection c = pool.poll();
        if (c == null) {
            return openConnection();
        }
        try {
            if (!c.isValid(2)) {
                closeQuietly(c);
                return openConnection();
            }
        } catch (SQLException e) {
            closeQuietly(c);
            return openConnection();
        }
        return c;
    }

    private void release(Connection c) {
        if (c == null) return;
        if (!pool.offer(c)) {
            closeQuietly(c);
        }
    }

    private void closeQuietly(Connection c) {
        try { if (c != null && !c.isClosed()) c.close(); } catch (SQLException ignored) {}
    }

    private String qualifiedTable(String container) {
        return schema + "." + container.replace("-", "_").replace(".", "_");
    }

    private void createIndex(Statement st, String table, String container) throws SQLException {
        String indexName = container.replace("-", "_").replace(".", "_") + "_vec_idx";
        if ("hnsw".equals(indexType)) {
            st.execute(
                "CREATE INDEX IF NOT EXISTS " + indexName +
                " ON " + table + " USING hnsw (" + vectorColumn + " " + indexOps + ")" +
                " WITH (m = " + hnswM + ", ef_construction = " + hnswEfConstruction + ")"
            );
        } else {
            st.execute(
                "CREATE INDEX IF NOT EXISTS " + indexName +
                " ON " + table + " USING ivfflat (" + vectorColumn + " " + indexOps + ")" +
                " WITH (lists = " + ivfflatLists + ")"
            );
        }
    }

    private void setProbes(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            if ("hnsw".equals(indexType)) {
                st.execute("SET hnsw.ef_search = " + probes);
            } else {
                st.execute("SET ivfflat.probes = " + probes);
            }
        }
    }

    private float[] generateVector(String seed) {
        Random rng = new Random(randomSeed ^ (long) seed.hashCode());
        float[] v = new float[dim];
        for (int i = 0; i < dim; i++) {
            v[i] = rng.nextFloat() * 2f - 1f;
        }
        return v;
    }

    /** Converts float[] to pgvector literal: [0.1,0.2,...] */
    private String vectorToSql(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    private String resolveOperator(String metric) {
        if ("l2".equals(metric))  return "<->";
        if ("ip".equals(metric))  return "<#>";
        return "<=>";
    }

    private String resolveIndexOps(String metric) {
        if ("l2".equals(metric))  return "vector_l2_ops";
        if ("ip".equals(metric))  return "vector_ip_ops";
        return "vector_cosine_ops";
    }
}
