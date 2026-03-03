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

package com.intel.cosbench.api.redis;

import static com.intel.cosbench.client.redis.RedisConstants.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import com.intel.cosbench.api.storage.NoneStorage;
import com.intel.cosbench.api.storage.StorageException;
import com.intel.cosbench.config.Config;
import com.intel.cosbench.log.Logger;

/**
 * COSBench StorageAPI implementation backed by Redis (or any Redis-compatible
 * store such as Google Cloud Memorystore).
 *
 * <p>Mapping of COSBench object-store semantics onto Redis:
 * <ul>
 *   <li>container  — key prefix (no dedicated data structure unless listing is needed)
 *   <li>object key — {@code <container><separator><objectName>}
 *   <li>createObject / getObject / deleteObject — SET / GET / DEL
 *   <li>createContainer — no-op (prefixes are implicit)
 *   <li>deleteContainer — SCAN + DEL on matching keys
 *   <li>getList        — SCAN returning newline-delimited key names
 * </ul>
 *
 * <p>Each COSBench worker gets its own {@link RedisStorage} instance, which owns
 * a {@link JedisPool}. The pool is sized via {@code maxTotal}/{@code maxIdle}/
 * {@code minIdle} config parameters — keep them at 1/1/0 unless workers share
 * the instance (they don't by default in COSBench).
 */
class RedisStorage extends NoneStorage {

    private String host;
    private int port;
    private String password;
    private int database;
    private int timeout;
    private boolean ssl;
    private String keySeparator;

    private JedisPool pool;

    public RedisStorage() {
    }

    @Override
    public void init(Config config, Logger logger) {
        super.init(config, logger);

        host = config.get(HOST_KEY, HOST_DEFAULT);
        port = config.getInt(PORT_KEY, PORT_DEFAULT);
        password = config.get(PASSWORD_KEY, PASSWORD_DEFAULT);
        database = config.getInt(DATABASE_KEY, DATABASE_DEFAULT);
        timeout = config.getInt(TIMEOUT_KEY, TIMEOUT_DEFAULT);
        ssl = config.getBoolean(SSL_KEY, SSL_DEFAULT);
        keySeparator = config.get(KEY_SEPARATOR_KEY, KEY_SEPARATOR_DEFAULT);

        int maxTotal = config.getInt(MAX_TOTAL_KEY, MAX_TOTAL_DEFAULT);
        int maxIdle = config.getInt(MAX_IDLE_KEY, MAX_IDLE_DEFAULT);
        int minIdle = config.getInt(MIN_IDLE_KEY, MIN_IDLE_DEFAULT);

        parms.put(HOST_KEY, host);
        parms.put(PORT_KEY, port);
        parms.put(DATABASE_KEY, database);
        parms.put(TIMEOUT_KEY, timeout);
        parms.put(SSL_KEY, ssl);
        parms.put(KEY_SEPARATOR_KEY, keySeparator);
        parms.put(MAX_TOTAL_KEY, maxTotal);
        parms.put(MAX_IDLE_KEY, maxIdle);
        parms.put(MIN_IDLE_KEY, minIdle);
        /* intentionally omit password from parms to avoid leaking it in logs */

        logger.debug("using redis storage config: {}", parms);

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

        logger.debug("redis connection pool initialised ({}:{})", host, port);
    }

    @Override
    public void dispose() {
        super.dispose();
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
        pool = null;
    }

    @Override
    public void abort() {
        super.abort();
        /* Jedis operations are blocking; interrupting the owning thread is the
           correct mechanism — COSBench worker thread interruption handles this. */
    }

    @Override
    public void createContainer(String container, Config config) {
        super.createContainer(container, config);
        /* Redis has no explicit namespace / bucket concept — the container name
           becomes a key prefix. Nothing to create. */
    }

    @Override
    public void deleteContainer(String container, Config config) {
        super.deleteContainer(container, config);
        String pattern = container + keySeparator + "*";
        try (Jedis jedis = pool.getResource()) {
            scanAndDelete(jedis, pattern);
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void createObject(String container, String object,
            InputStream data, long length, Config config) {
        super.createObject(container, object, data, length, config);
        byte[] key = buildKey(container, object);
        byte[] value = readFully(data, length);
        try (Jedis jedis = pool.getResource()) {
            jedis.set(key, value);
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    @Override
    public InputStream getObject(String container, String object, Config config) {
        super.getObject(container, object, config);
        byte[] key = buildKey(container, object);
        try (Jedis jedis = pool.getResource()) {
            byte[] value = jedis.get(key);
            if (value == null) {
                throw new StorageException("key not found: " + new String(key));
            }
            return new ByteArrayInputStream(value);
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

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

    @Override
    public InputStream getList(String container, String object, Config config) {
        super.getList(container, object, config);
        String pattern = container + keySeparator + "*";
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

    private byte[] buildKey(String container, String object) {
        return (container + keySeparator + object).getBytes();
    }

    private byte[] readFully(InputStream data, long length) {
        try {
            if (length > Integer.MAX_VALUE) {
                throw new StorageException("object too large for Redis adapter: " + length + " bytes");
            }
            int size = (int) length;
            byte[] buf = new byte[size];
            int offset = 0;
            while (offset < size) {
                int read = data.read(buf, offset, size - offset);
                if (read == -1) {
                    throw new StorageException("unexpected end of input after " + offset + " bytes (expected " + size + ")");
                }
                offset += read;
            }
            return buf;
        } catch (IOException e) {
            throw new StorageException(e);
        }
    }

    private void scanAndDelete(Jedis jedis, String pattern) {
        String cursor = "0";
        do {
            redis.clients.jedis.ScanResult<String> result =
                    jedis.scan(cursor, new redis.clients.jedis.ScanParams().match(pattern).count(1000));
            cursor = result.getCursor();
            if (!result.getResult().isEmpty()) {
                jedis.del(result.getResult().toArray(new String[0]));
            }
        } while (!"0".equals(cursor));
    }
}
