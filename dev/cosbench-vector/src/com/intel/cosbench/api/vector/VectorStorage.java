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

package com.intel.cosbench.api.vector;

import static com.intel.cosbench.client.vector.VectorConstants.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.util.SafeEncoder;

import com.intel.cosbench.api.storage.NoneStorage;
import com.intel.cosbench.api.storage.StorageException;
import com.intel.cosbench.config.Config;
import com.intel.cosbench.log.Logger;

/**
 * COSBench StorageAPI implementation for Vector Similarity Search (VSS)
 * benchmarking against any RediSearch-compatible backend:
 * Redis Stack, Redis 8+, Valkey + valkey-search, or DragonflyDB.
 *
 * <p>COSBench operation mapping:
 * <ul>
 *   <li>init        — FT.CREATE index (idempotent; drops and recreates if exists)
 *   <li>write       — HSET key embedding &lt;float32 binary blob&gt;
 *   <li>read        — FT.SEARCH idx "*=>[KNN K @field $vec]" (KNN query)
 *   <li>cleanup     — DEL all HASH documents under the container prefix
 *   <li>dispose     — FT.DROPINDEX (removes the index, documents kept)
 * </ul>
 *
 * <p>Vectors are generated deterministically using a seeded PRNG so that
 * benchmarks are reproducible across runs without storing vectors externally.
 */
class VectorStorage extends NoneStorage {

    private String host;
    private int port;
    private String password;
    private int database;
    private int timeout;
    private boolean ssl;

    private String indexName;
    private String keyPrefix;
    private String vectorField;
    private int dim;
    private String algorithm;
    private String distanceMetric;
    private int knn;
    private int hnswM;
    private int hnswEfConstruction;
    private long randomSeed;

    private JedisPool pool;

    public VectorStorage() {
    }

    @Override
    public void init(Config config, Logger logger) {
        super.init(config, logger);

        host            = config.get(HOST_KEY, HOST_DEFAULT);
        port            = config.getInt(PORT_KEY, PORT_DEFAULT);
        password        = config.get(PASSWORD_KEY, PASSWORD_DEFAULT);
        database        = config.getInt(DATABASE_KEY, DATABASE_DEFAULT);
        timeout         = config.getInt(TIMEOUT_KEY, TIMEOUT_DEFAULT);
        ssl             = config.getBoolean(SSL_KEY, SSL_DEFAULT);

        indexName       = config.get(INDEX_NAME_KEY, INDEX_NAME_DEFAULT);
        keyPrefix       = config.get(KEY_PREFIX_KEY, KEY_PREFIX_DEFAULT);
        vectorField     = config.get(VECTOR_FIELD_KEY, VECTOR_FIELD_DEFAULT);
        dim             = config.getInt(DIM_KEY, DIM_DEFAULT);
        algorithm       = config.get(ALGORITHM_KEY, ALGORITHM_DEFAULT).toUpperCase();
        distanceMetric  = config.get(DISTANCE_METRIC_KEY, DISTANCE_METRIC_DEFAULT).toUpperCase();
        knn             = config.getInt(KNN_KEY, KNN_DEFAULT);
        hnswM           = config.getInt(HNSW_M_KEY, HNSW_M_DEFAULT);
        hnswEfConstruction = config.getInt(HNSW_EF_CONSTRUCTION_KEY, HNSW_EF_CONSTRUCTION_DEFAULT);
        randomSeed      = config.getLong(RANDOM_SEED_KEY, RANDOM_SEED_DEFAULT);

        int maxTotal = config.getInt(MAX_TOTAL_KEY, MAX_TOTAL_DEFAULT);
        int maxIdle  = config.getInt(MAX_IDLE_KEY,  MAX_IDLE_DEFAULT);
        int minIdle  = config.getInt(MIN_IDLE_KEY,  MIN_IDLE_DEFAULT);

        parms.put(HOST_KEY, host);
        parms.put(PORT_KEY, port);
        parms.put(DATABASE_KEY, database);
        parms.put(TIMEOUT_KEY, timeout);
        parms.put(SSL_KEY, ssl);
        parms.put(INDEX_NAME_KEY, indexName);
        parms.put(KEY_PREFIX_KEY, keyPrefix);
        parms.put(VECTOR_FIELD_KEY, vectorField);
        parms.put(DIM_KEY, dim);
        parms.put(ALGORITHM_KEY, algorithm);
        parms.put(DISTANCE_METRIC_KEY, distanceMetric);
        parms.put(KNN_KEY, knn);
        parms.put(MAX_TOTAL_KEY, maxTotal);
        parms.put(MAX_IDLE_KEY, maxIdle);
        parms.put(MIN_IDLE_KEY, minIdle);

        logger.debug("using vector storage config: {}", parms);

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(maxTotal);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);
        poolConfig.setTestOnBorrow(false);
        poolConfig.setTestOnReturn(false);

        if (password != null && !password.isEmpty()) {
            pool = new JedisPool(poolConfig, host, port, timeout, password, database, ssl);
        } else {
            pool = new JedisPool(poolConfig, host, port, timeout, null, database, ssl);
        }

        logger.debug("vector storage pool initialised ({}:{} index={} dim={} algo={})",
                new Object[]{host, port, indexName, dim, algorithm});
    }

    @Override
    public void dispose() {
        super.dispose();
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
        pool = null;
    }

    /**
     * createContainer → FT.CREATE the vector index.
     * Idempotent: drops any existing index with the same name first.
     */
    @Override
    public void createContainer(String container, Config config) {
        super.createContainer(container, config);
        try (Jedis jedis = pool.getResource()) {
            dropIndexIfExists(jedis);
            createIndex(jedis, container);
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    /**
     * deleteContainer → FT.DROPINDEX (index only; HASH docs deleted separately by cleanup).
     */
    @Override
    public void deleteContainer(String container, Config config) {
        super.deleteContainer(container, config);
        try (Jedis jedis = pool.getResource()) {
            dropIndexIfExists(jedis);
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    /**
     * createObject → HSET key embedding &lt;binary float32 vector&gt;.
     * The {@code data} stream content is ignored; a pseudo-random vector is
     * generated from the object name to avoid external data dependency.
     */
    @Override
    public void createObject(String container, String object,
            InputStream data, long length, Config config) {
        super.createObject(container, object, data, length, config);
        byte[] key   = buildKey(container, object);
        byte[] vec   = generateVector(object);
        try (Jedis jedis = pool.getResource()) {
            jedis.hset(key, SafeEncoder.encode(vectorField), vec);
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    /**
     * getObject → FT.SEARCH KNN query using a vector derived from the object name.
     * Returns a newline-delimited list of matching document keys as the stream.
     */
    @Override
    public InputStream getObject(String container, String object, Config config) {
        super.getObject(container, object, config);
        byte[] queryVec = generateVector(object);
        try (Jedis jedis = pool.getResource()) {
            Object raw = knnSearch(jedis, queryVec);
            String result = raw != null ? raw.toString() : "";
            return new ByteArrayInputStream(result.getBytes("UTF-8"));
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    /**
     * deleteObject → DEL the HASH document for the given key.
     */
    @Override
    public void deleteObject(String container, String object, Config config) {
        super.deleteObject(container, object, config);
        byte[] key = buildKey(container, object);
        try (Jedis jedis = pool.getResource()) {
            jedis.del(key);
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    /**
     * getList → SCAN all HASH keys under the container prefix.
     */
    @Override
    public InputStream getList(String container, String object, Config config) {
        super.getList(container, object, config);
        String pattern = keyPrefix + container + ":*";
        StringBuilder sb = new StringBuilder();
        try (Jedis jedis = pool.getResource()) {
            String cursor = "0";
            do {
                redis.clients.jedis.ScanResult<String> result =
                        jedis.scan(cursor, new redis.clients.jedis.ScanParams().match(pattern).count(1000));
                cursor = result.getCursor();
                for (String k : result.getResult()) {
                    sb.append(k).append('\n');
                }
            } while (!"0".equals(cursor));
        } catch (Exception e) {
            throw new StorageException(e);
        }
        return new ByteArrayInputStream(sb.toString().getBytes());
    }

    // --------------------------------------------------------------------------
    // Private helpers
    // --------------------------------------------------------------------------

    private byte[] buildKey(String container, String object) {
        return SafeEncoder.encode(keyPrefix + container + ":" + object);
    }

    /**
     * Generates a reproducible float32 vector for the given seed string.
     * Using the object name as seed ensures the same object always yields
     * the same vector, making write/read pairs consistent.
     */
    private byte[] generateVector(String seed) {
        Random rng = new Random(randomSeed ^ (long) seed.hashCode());
        ByteBuffer buf = ByteBuffer.allocate(4 * dim);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < dim; i++) {
            buf.putFloat(rng.nextFloat() * 2f - 1f);
        }
        return buf.array();
    }

    private void createIndex(Jedis jedis, String container) {
        String prefix = keyPrefix + container + ":";
        if ("HNSW".equals(algorithm)) {
            jedis.sendCommand(VectorCommand.CREATE,
                    indexName, "ON", "HASH", "PREFIX", "1", prefix,
                    "SCHEMA", vectorField, "VECTOR", "HNSW",
                    "10",
                    "TYPE", "FLOAT32",
                    "DIM", String.valueOf(dim),
                    "DISTANCE_METRIC", distanceMetric,
                    "M", String.valueOf(hnswM),
                    "EF_CONSTRUCTION", String.valueOf(hnswEfConstruction));
        } else {
            jedis.sendCommand(VectorCommand.CREATE,
                    indexName, "ON", "HASH", "PREFIX", "1", prefix,
                    "SCHEMA", vectorField, "VECTOR", "FLAT",
                    "6",
                    "TYPE", "FLOAT32",
                    "DIM", String.valueOf(dim),
                    "DISTANCE_METRIC", distanceMetric);
        }
    }

    private void dropIndexIfExists(Jedis jedis) {
        try {
            jedis.sendCommand(VectorCommand.DROPINDEX, indexName);
        } catch (Exception ignored) {
            // Index does not exist — that is fine
        }
    }

    private Object knnSearch(Jedis jedis, byte[] queryVec) {
        String query = "*=>[KNN " + knn + " @" + vectorField + " $query_vec AS score]";
        return jedis.sendCommand(VectorCommand.SEARCH,
                SafeEncoder.encode(indexName),
                SafeEncoder.encode(query),
                SafeEncoder.encode("PARAMS"),
                SafeEncoder.encode("2"),
                SafeEncoder.encode("query_vec"),
                queryVec,
                SafeEncoder.encode("SORTBY"),
                SafeEncoder.encode("score"),
                SafeEncoder.encode("ASC"),
                SafeEncoder.encode("DIALECT"),
                SafeEncoder.encode("2"));
    }
}
