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

package com.intel.cosbench.client.pgvector;

public interface PgVectorConstants {

    // --------------------------------------------------------------------------
    // CONNECTION
    // --------------------------------------------------------------------------

    String HOST_KEY     = "host";
    String HOST_DEFAULT = "localhost";

    String PORT_KEY     = "port";
    int    PORT_DEFAULT = 5432;

    String DATABASE_KEY     = "database";
    String DATABASE_DEFAULT = "postgres";

    String USERNAME_KEY     = "username";
    String USERNAME_DEFAULT = "postgres";

    String PASSWORD_KEY     = "password";
    String PASSWORD_DEFAULT = "";

    String SSL_KEY     = "ssl";
    boolean SSL_DEFAULT = false;

    /** JDBC connection pool size per worker. */
    String POOL_SIZE_KEY     = "poolSize";
    int    POOL_SIZE_DEFAULT = 4;

    /** JDBC socket / query timeout in seconds (0 = no timeout). */
    String TIMEOUT_KEY     = "timeout";
    int    TIMEOUT_DEFAULT = 30;

    // --------------------------------------------------------------------------
    // VECTOR TABLE
    // --------------------------------------------------------------------------

    /**
     * Schema that holds vector tables.
     * Default: {@code public}
     */
    String SCHEMA_KEY     = "schema";
    String SCHEMA_DEFAULT = "public";

    /**
     * Name of the column that holds the vector.
     * Default: {@code embedding}
     */
    String VECTOR_COLUMN_KEY     = "vectorColumn";
    String VECTOR_COLUMN_DEFAULT = "embedding";

    /**
     * Name of the primary-key / document-id column.
     * Default: {@code id}
     */
    String ID_COLUMN_KEY     = "idColumn";
    String ID_COLUMN_DEFAULT = "id";

    /** Vector dimensionality (number of float32 components). */
    String DIM_KEY     = "dim";
    int    DIM_DEFAULT = 128;

    // --------------------------------------------------------------------------
    // VECTOR INDEX
    // --------------------------------------------------------------------------

    /**
     * Index type: {@code hnsw} (pgvector 0.5.0+) or {@code ivfflat}.
     * Default: {@code hnsw}
     */
    String INDEX_TYPE_KEY     = "indexType";
    String INDEX_TYPE_DEFAULT = "hnsw";

    /**
     * Distance operator / access method:
     * {@code cosine}  → vector_cosine_ops  / {@code <=>}
     * {@code l2}      → vector_l2_ops      / {@code <->}
     * {@code ip}      → vector_ip_ops      / {@code <#>}
     * Default: {@code cosine}
     */
    String DISTANCE_METRIC_KEY     = "distanceMetric";
    String DISTANCE_METRIC_DEFAULT = "cosine";

    /** HNSW: max connections per layer (m). Default: 16 */
    String HNSW_M_KEY     = "hnswM";
    int    HNSW_M_DEFAULT = 16;

    /** HNSW: size of dynamic candidate list at build time. Default: 64 */
    String HNSW_EF_CONSTRUCTION_KEY     = "hnswEfConstruction";
    int    HNSW_EF_CONSTRUCTION_DEFAULT = 64;

    /** IVFFlat: number of inverted lists. Default: 100 */
    String IVFFLAT_LISTS_KEY     = "ivfflatLists";
    int    IVFFLAT_LISTS_DEFAULT = 100;

    /** IVFFlat / HNSW ef_search: probes at query time. Default: 10 */
    String PROBES_KEY     = "probes";
    int    PROBES_DEFAULT = 10;

    // --------------------------------------------------------------------------
    // KNN SEARCH
    // --------------------------------------------------------------------------

    /** Number of nearest neighbours returned per query. Default: 10 */
    String KNN_KEY     = "knn";
    int    KNN_DEFAULT = 10;

    // --------------------------------------------------------------------------
    // SYNTHETIC VECTOR GENERATION
    // --------------------------------------------------------------------------

    /** Seed for reproducible pseudo-random vector generation. Default: 42 */
    String RANDOM_SEED_KEY     = "randomSeed";
    long   RANDOM_SEED_DEFAULT = 42L;
}
