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

package com.intel.cosbench.api.couchbase;

import static com.intel.cosbench.client.couchbase.CouchbaseConstants.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.env.ClusterEnvironment;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.java.kv.MutationResult;
import com.couchbase.client.java.codec.RawBinaryTranscoder;
import com.couchbase.client.java.kv.UpsertOptions;
import com.couchbase.client.java.kv.GetOptions;
import com.couchbase.client.java.query.QueryResult;
import com.couchbase.client.java.query.QueryOptions;

import com.intel.cosbench.api.storage.NoneStorage;
import com.intel.cosbench.api.storage.StorageException;
import com.intel.cosbench.config.Config;
import com.intel.cosbench.log.Logger;

class CouchbaseStorage extends NoneStorage {

    private String host;
    private String bucketName;
    private String username;
    private String password;
    private int connectTimeout;
    private int kvTimeout;
    private String keySeparator;
    private String collectionName;
    private boolean useScope;

    private ClusterEnvironment env;
    private Cluster cluster;
    private Bucket bucket;

    public CouchbaseStorage() {
    }

    @Override
    public void init(Config config, Logger logger) {
        super.init(config, logger);

        host = config.get(HOST_KEY, HOST_DEFAULT);
        bucketName = config.get(BUCKET_KEY, BUCKET_DEFAULT);
        username = config.get(USERNAME_KEY, USERNAME_DEFAULT);
        password = config.get(PASSWORD_KEY, PASSWORD_DEFAULT);
        connectTimeout = config.getInt(CONNECT_TIMEOUT_KEY, CONNECT_TIMEOUT_DEFAULT);
        kvTimeout = config.getInt(KV_TIMEOUT_KEY, KV_TIMEOUT_DEFAULT);
        keySeparator = config.get(KEY_SEPARATOR_KEY, KEY_SEPARATOR_DEFAULT);
        collectionName = config.get(COLLECTION_KEY, COLLECTION_DEFAULT);
        useScope = config.getBoolean(USE_SCOPE_KEY, USE_SCOPE_DEFAULT);

        parms.put(HOST_KEY, host);
        parms.put(BUCKET_KEY, bucketName);
        parms.put(USERNAME_KEY, username);
        parms.put(CONNECT_TIMEOUT_KEY, connectTimeout);
        parms.put(KV_TIMEOUT_KEY, kvTimeout);
        parms.put(KEY_SEPARATOR_KEY, keySeparator);
        parms.put(COLLECTION_KEY, collectionName);
        parms.put(USE_SCOPE_KEY, useScope);

        logger.debug("using couchbase storage config: {}", parms);

        env = ClusterEnvironment.builder()
                .timeoutConfig(tc -> tc
                        .connectTimeout(Duration.ofMillis(connectTimeout))
                        .kvTimeout(Duration.ofMillis(kvTimeout)))
                .build();

        cluster = Cluster.connect(host,
                ClusterOptions.clusterOptions(username, password).environment(env));

        bucket = cluster.bucket(bucketName);
        bucket.waitUntilReady(Duration.ofMillis(connectTimeout));

        logger.debug("couchbase connected to " + username + "@" + host + "/" + bucketName);
    }

    @Override
    public void dispose() {
        super.dispose();
        if (cluster != null) {
            try { cluster.disconnect(); } catch (Exception ignored) {}
            cluster = null;
        }
        if (env != null) {
            try { env.shutdown(); } catch (Exception ignored) {}
            env = null;
        }
        bucket = null;
    }

    @Override
    public void abort() {
        super.abort();
    }

    @Override
    public void createContainer(String container, Config config) {
        super.createContainer(container, config);
    }

    @Override
    public void deleteContainer(String container, Config config) {
        super.deleteContainer(container, config);
        if (useScope) {
            return;
        }
        String prefix = container + keySeparator;
        Collection col = getCollection(container);
        String bucketRef = "`" + bucketName + "`";
        String stmt = "DELETE FROM " + bucketRef
                + " WHERE META().id LIKE '" + prefix.replace("'", "\\'") + "%'";
        try {
            cluster.query(stmt, QueryOptions.queryOptions()
                    .timeout(Duration.ofSeconds(300)));
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void createObject(String container, String object,
            InputStream data, long length, Config config) {
        super.createObject(container, object, data, length, config);
        String key = buildKey(container, object);
        byte[] value = readFully(data, length);
        Collection col = getCollection(container);
        try {
            col.upsert(key, value,
                    UpsertOptions.upsertOptions().transcoder(RawBinaryTranscoder.INSTANCE)
                            .timeout(Duration.ofMillis(kvTimeout)));
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    @Override
    public InputStream getObject(String container, String object, Config config) {
        super.getObject(container, object, config);
        String key = buildKey(container, object);
        Collection col = getCollection(container);
        try {
            GetResult result = col.get(key,
                    GetOptions.getOptions().transcoder(RawBinaryTranscoder.INSTANCE)
                            .timeout(Duration.ofMillis(kvTimeout)));
            byte[] value = result.contentAsBytes();
            return new ByteArrayInputStream(value);
        } catch (DocumentNotFoundException e) {
            throw new StorageException("key not found: " + key);
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void deleteObject(String container, String object, Config config) {
        super.deleteObject(container, object, config);
        String key = buildKey(container, object);
        Collection col = getCollection(container);
        try {
            col.remove(key,
                    com.couchbase.client.java.kv.RemoveOptions.removeOptions()
                            .timeout(Duration.ofMillis(kvTimeout)));
        } catch (DocumentNotFoundException ignored) {
            // already gone — treat as success
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    @Override
    public InputStream getList(String container, String object, Config config) {
        super.getList(container, object, config);
        String prefix = useScope ? "" : (container + keySeparator);
        String bucketRef = "`" + bucketName + "`";
        String stmt = "SELECT META().id AS id FROM " + bucketRef
                + (prefix.isEmpty() ? "" : " WHERE META().id LIKE '" + prefix.replace("'", "\\'") + "%'")
                + " LIMIT 1000";
        StringBuilder sb = new StringBuilder();
        try {
            QueryResult qr = cluster.query(stmt,
                    QueryOptions.queryOptions().timeout(Duration.ofSeconds(60)));
            for (com.couchbase.client.java.json.JsonObject row : qr.rowsAsObject()) {
                sb.append(row.getString("id")).append('\n');
            }
        } catch (Exception e) {
            throw new StorageException(e);
        }
        return new ByteArrayInputStream(sb.toString().getBytes());
    }

    private Collection getCollection(String container) {
        if (useScope) {
            return bucket.scope(container).collection(collectionName);
        }
        return bucket.defaultScope().collection(collectionName);
    }

    private String buildKey(String container, String object) {
        if (useScope) {
            return object;
        }
        return container + keySeparator + object;
    }

    private byte[] readFully(InputStream data, long length) {
        try {
            if (length > Integer.MAX_VALUE) {
                throw new StorageException(
                        "object too large for Couchbase adapter: " + length + " bytes");
            }
            int size = (int) length;
            byte[] buf = new byte[size];
            int offset = 0;
            while (offset < size) {
                int read = data.read(buf, offset, size - offset);
                if (read == -1) {
                    throw new StorageException("unexpected end of input after " + offset
                            + " bytes (expected " + size + ")");
                }
                offset += read;
            }
            return buf;
        } catch (IOException e) {
            throw new StorageException(e);
        }
    }
}
