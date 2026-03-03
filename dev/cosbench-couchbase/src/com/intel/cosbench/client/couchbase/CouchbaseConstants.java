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

package com.intel.cosbench.client.couchbase;

/**
 * Configuration constants for the Couchbase storage adapter.
 *
 * <p>Each COSBench "container" maps to a Couchbase scope, and each
 * COSBench "object" maps to a document stored in the default collection
 * of that scope (or a configured collection name).
 * The document key is the raw object name.
 */
public interface CouchbaseConstants {

    // --------------------------------------------------------------------------
    // CONNECTION
    // --------------------------------------------------------------------------

    /** Couchbase cluster hostname or IP address (comma-separated for multiple nodes). */
    String HOST_KEY = "host";
    String HOST_DEFAULT = "localhost";

    /** Couchbase bucket to use for all operations. */
    String BUCKET_KEY = "bucket";
    String BUCKET_DEFAULT = "default";

    // --------------------------------------------------------------------------
    // AUTHENTICATION
    // --------------------------------------------------------------------------

    /** Couchbase username. */
    String USERNAME_KEY = "username";
    String USERNAME_DEFAULT = "Administrator";

    /** Couchbase password. */
    String PASSWORD_KEY = "password";
    String PASSWORD_DEFAULT = "password";

    // --------------------------------------------------------------------------
    // TIMEOUTS (milliseconds)
    // --------------------------------------------------------------------------

    /** Connect timeout in milliseconds. */
    String CONNECT_TIMEOUT_KEY = "connectTimeout";
    int CONNECT_TIMEOUT_DEFAULT = 10000;

    /** KV operation timeout in milliseconds. */
    String KV_TIMEOUT_KEY = "kvTimeout";
    int KV_TIMEOUT_DEFAULT = 5000;

    // --------------------------------------------------------------------------
    // KEY STRATEGY
    // --------------------------------------------------------------------------

    /**
     * Separator between container name and object name when forming the
     * document key. Resulting key: {@code <container><separator><object>}.
     * Set to empty string to use just the object name as the key.
     */
    String KEY_SEPARATOR_KEY = "keySeparator";
    String KEY_SEPARATOR_DEFAULT = ":";

    /**
     * Couchbase collection name to use within each scope.
     * Defaults to {@code _default} — the default collection in any scope.
     */
    String COLLECTION_KEY = "collection";
    String COLLECTION_DEFAULT = "_default";

    /**
     * If true, the container name is used as a Couchbase scope name, and
     * objects are stored in the configured collection within that scope.
     * If false (default), all objects are stored in the default scope's
     * configured collection, with keys prefixed by the container name.
     */
    String USE_SCOPE_KEY = "useScope";
    boolean USE_SCOPE_DEFAULT = false;

}
