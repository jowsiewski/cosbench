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

package com.intel.cosbench.client.vector;

/**
 * Configuration constants for the Vector Search storage adapter.
 *
 * <p>Compatible with any RediSearch-compatible backend:
 * <ul>
 *   <li>Redis Stack (bundled RediSearch module)
 *   <li>Redis 8+ (native query engine)
 *   <li>Valkey + valkey-search module
 *   <li>DragonflyDB (native vector search since v1.13)
 * </ul>
 */
public interface VectorConstants {

    // --------------------------------------------------------------------------
    // CONNECTION
    // --------------------------------------------------------------------------

    /** Server hostname or IP address. */
    String HOST_KEY = "host";
    String HOST_DEFAULT = "localhost";

    /** Server port. */
    String PORT_KEY = "port";
    int PORT_DEFAULT = 6379;

    /** AUTH password (leave empty if no auth). */
    String PASSWORD_KEY = "password";
    String PASSWORD_DEFAULT = "";

    /** Logical database index. */
    String DATABASE_KEY = "database";
    int DATABASE_DEFAULT = 0;

    /** Socket connect/read timeout in milliseconds. */
    String TIMEOUT_KEY = "timeout";
    int TIMEOUT_DEFAULT = 30000;

    /** Enable TLS/SSL for the connection. */
    String SSL_KEY = "ssl";
    boolean SSL_DEFAULT = false;

    // --------------------------------------------------------------------------
    // CONNECTION POOL
    // --------------------------------------------------------------------------

    String MAX_TOTAL_KEY = "maxTotal";
    int MAX_TOTAL_DEFAULT = 8;

    String MAX_IDLE_KEY = "maxIdle";
    int MAX_IDLE_DEFAULT = 8;

    String MIN_IDLE_KEY = "minIdle";
    int MIN_IDLE_DEFAULT = 0;

    // --------------------------------------------------------------------------
    // VECTOR INDEX
    // --------------------------------------------------------------------------

    /**
     * Name of the RediSearch index to create / query.
     * Default: {@code cosbench-vec-idx}
     */
    String INDEX_NAME_KEY = "indexName";
    String INDEX_NAME_DEFAULT = "cosbench-vec-idx";

    /**
     * Key prefix for HASH documents.
     * Resulting key format: {@code <keyPrefix><container>:<objectName>}
     */
    String KEY_PREFIX_KEY = "keyPrefix";
    String KEY_PREFIX_DEFAULT = "vec:";

    /**
     * Name of the HASH field that holds the binary vector blob.
     * Default: {@code embedding}
     */
    String VECTOR_FIELD_KEY = "vectorField";
    String VECTOR_FIELD_DEFAULT = "embedding";

    /**
     * Dimensionality of each vector (number of float32 components).
     * Must match across all operations in a workload.
     * Default: {@code 128}
     */
    String DIM_KEY = "dim";
    int DIM_DEFAULT = 128;

    /**
     * Vector index algorithm: {@code FLAT} (brute-force) or {@code HNSW}.
     * FLAT is accurate; HNSW is approximate but faster for large datasets.
     * Default: {@code HNSW}
     */
    String ALGORITHM_KEY = "algorithm";
    String ALGORITHM_DEFAULT = "HNSW";

    /**
     * Distance metric: {@code COSINE}, {@code L2}, or {@code IP} (inner product).
     * Default: {@code COSINE}
     */
    String DISTANCE_METRIC_KEY = "distanceMetric";
    String DISTANCE_METRIC_DEFAULT = "COSINE";

    /**
     * Number of nearest neighbours (K) to retrieve in each KNN search.
     * Default: {@code 10}
     */
    String KNN_KEY = "knn";
    int KNN_DEFAULT = 10;

    /**
     * HNSW-specific: number of bi-directional links per node.
     * Ignored when algorithm=FLAT.
     * Default: {@code 16}
     */
    String HNSW_M_KEY = "hnswM";
    int HNSW_M_DEFAULT = 16;

    /**
     * HNSW-specific: size of the dynamic candidate list during index construction.
     * Ignored when algorithm=FLAT.
     * Default: {@code 200}
     */
    String HNSW_EF_CONSTRUCTION_KEY = "hnswEfConstruction";
    int HNSW_EF_CONSTRUCTION_DEFAULT = 200;

    /**
     * Seed for the pseudo-random vector generator used in write operations.
     * Using a fixed seed produces reproducible but varied vectors.
     * Default: {@code 42}
     */
    String RANDOM_SEED_KEY = "randomSeed";
    long RANDOM_SEED_DEFAULT = 42L;
}
