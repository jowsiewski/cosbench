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

package com.intel.cosbench.client.redis;

/**
 * Configuration constants for the Redis/Memorystore storage adapter.
 *
 * <p>Supports standalone Redis, Redis Sentinel, Redis Cluster, and
 * Google Cloud Memorystore (Redis-compatible) via SSL/TLS.
 */
public interface RedisConstants {

    // --------------------------------------------------------------------------
    // CONNECTION — standalone mode
    // --------------------------------------------------------------------------

    /** Redis server hostname or IP address. */
    String HOST_KEY = "host";
    String HOST_DEFAULT = "localhost";

    /** Redis server port. */
    String PORT_KEY = "port";
    int PORT_DEFAULT = 6379;

    // --------------------------------------------------------------------------
    // CONNECTION POOL
    // --------------------------------------------------------------------------

    /** Maximum total connections in pool (per worker). */
    String MAX_TOTAL_KEY = "maxTotal";
    int MAX_TOTAL_DEFAULT = 8;

    /** Maximum idle connections in pool. */
    String MAX_IDLE_KEY = "maxIdle";
    int MAX_IDLE_DEFAULT = 8;

    /** Minimum idle connections in pool. */
    String MIN_IDLE_KEY = "minIdle";
    int MIN_IDLE_DEFAULT = 0;

    // --------------------------------------------------------------------------
    // TIMEOUTS
    // --------------------------------------------------------------------------

    /** Socket connect/read timeout in milliseconds. */
    String TIMEOUT_KEY = "timeout";
    int TIMEOUT_DEFAULT = 30000;

    // --------------------------------------------------------------------------
    // AUTHENTICATION
    // --------------------------------------------------------------------------

    /** Redis AUTH password (leave empty if no auth). */
    String PASSWORD_KEY = "password";
    String PASSWORD_DEFAULT = "";

    /**
     * Redis logical database index (0-15 for most configurations).
     * Google Memorystore only supports DB 0.
     */
    String DATABASE_KEY = "database";
    int DATABASE_DEFAULT = 0;

    // --------------------------------------------------------------------------
    // TLS / SSL (required for Google Cloud Memorystore in-transit encryption)
    // --------------------------------------------------------------------------

    /**
     * Enable TLS/SSL for the connection.
     * Set to {@code true} for Google Cloud Memorystore with in-transit
     * encryption enabled (uses port 6378 by default on Memorystore).
     */
    String SSL_KEY = "ssl";
    boolean SSL_DEFAULT = false;

    // --------------------------------------------------------------------------
    // KEY STRATEGY
    // --------------------------------------------------------------------------

    /**
     * Key separator used when building Redis keys from container + object names.
     * Resulting key format: {@code <container><separator><object>}
     * e.g. {@code bench:obj-000001}
     */
    String KEY_SEPARATOR_KEY = "keySeparator";
    String KEY_SEPARATOR_DEFAULT = ":";

    /**
     * Maximum byte size of a single value that will be read back entirely into
     * memory during {@code getObject}. Values larger than this are streamed via
     * a chunked approach (still fully materialised — Redis has no partial read).
     * Default: 512 MB (Redis limit). Reduce for memory-constrained drivers.
     */
    String MAX_VALUE_SIZE_KEY = "maxValueSize";
    long MAX_VALUE_SIZE_DEFAULT = 512L * 1024 * 1024; // 512 MB

}
