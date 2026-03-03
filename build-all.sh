#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${1:-$SCRIPT_DIR}"
DEV="$ROOT/dev"
DIST="$ROOT/dist/osgi"
LIBS="$DIST/libs"
PLUGINS="$DIST/plugins"
VERSION=$(cat "$ROOT/VERSION")
OSGI_JAR="$DIST/org.eclipse.osgi-3.7.0.v20110613.jar"
OSGI_CORE_JAR="$DIST/osgi.core-6.0.0.jar"
ALL_LIBS=$(find "$LIBS" -name "*.jar" | tr '\n' ':')
OSGI_CP="$OSGI_CORE_JAR:$OSGI_JAR:$ALL_LIBS"

echo "=== COSBench Full Build (version $VERSION) ==="

compile() {
    local NAME="$1"
    local CP="$2"
    local SRC="$DEV/$NAME/src"
    local OUT="$DEV/$NAME/bin"
    local EXTRA_JARS="$3"

    echo -n "  $NAME ... "
    mkdir -p "$OUT"
    local SOURCES
    SOURCES=$(find "$SRC" -name "*.java" -not -path "*/.ecsmeta/*" 2>/dev/null || true)
    if [ -z "$SOURCES" ]; then
        echo "no sources, skipping"
        return
    fi
    local CP_FULL="$CP"
    if [ -n "$EXTRA_JARS" ]; then
        CP_FULL="$EXTRA_JARS:$CP"
    fi
    # shellcheck disable=SC2086
    javac -source 1.8 -target 1.8 -encoding UTF-8 -cp "$CP_FULL" -d "$OUT" $SOURCES 2>&1
    echo "OK"
}

compile_optional() {
    local NAME="$1"
    local CP="$2"
    local EXTRA_JARS="$3"
    echo -n "  $NAME (optional) ... "
    compile "$NAME" "$CP" "$EXTRA_JARS" && true || echo "  SKIPPED (missing SDK — expected)"
}

assemble() {
    local NAME="$1"
    local OUT="$PLUGINS/${NAME}_${VERSION}"
    local SRC_BIN="$DEV/$NAME/bin"
    local SRC_DIR="$DEV/$NAME"

    rm -rf "$OUT"
    mkdir -p "$OUT"
    [ -d "$SRC_BIN" ] && cp -r "$SRC_BIN/." "$OUT/"
    cp -r "$SRC_DIR/META-INF" "$OUT/"
    for jar in "$SRC_DIR"/*.jar "$SRC_DIR"/*.JAR; do
        [ -f "$jar" ] && cp "$jar" "$OUT/" || true
    done
    for f in "$SRC_DIR"/*.properties "$SRC_DIR"/*.xml; do
        [ -f "$f" ] && cp "$f" "$OUT/" || true
    done
    [ -d "$SRC_DIR/WEB-INF" ] && cp -r "$SRC_DIR/WEB-INF" "$OUT/" || true
    [ -d "$SRC_DIR/resources" ] && cp -r "$SRC_DIR/resources" "$OUT/" || true
    [ -f "$SRC_DIR/index.html" ] && cp "$SRC_DIR/index.html" "$OUT/" || true
}

mkdir -p "$PLUGINS"

echo ""
echo "--- Layer 1: cosbench-log ---"
compile "cosbench-log"    "$OSGI_CP"
LOG="$DEV/cosbench-log/bin"

echo ""
echo "--- Layer 2: cosbench-config, cosbench-log4j, cosbench-castor ---"
compile "cosbench-config"  "$LOG:$OSGI_CP"
compile "cosbench-log4j"   "$LOG:$OSGI_CP"
compile "cosbench-castor"  "$LOG:$OSGI_CP"
CFG="$DEV/cosbench-config/bin"

echo ""
echo "--- Layer 3: cosbench-api, cosbench-http, cosbench-cdmi-util ---"
compile "cosbench-api"      "$LOG:$CFG:$OSGI_CP"
compile "cosbench-http"     "$LOG:$CFG:$OSGI_CP"
compile "cosbench-cdmi-util" "$LOG:$CFG:$OSGI_CP"
API="$DEV/cosbench-api/bin"
HTTP="$DEV/cosbench-http/bin"

echo ""
echo "--- Layer 4: cosbench-core ---"
compile "cosbench-core"  "$LOG:$CFG:$API:$HTTP:$OSGI_CP"
CORE="$DEV/cosbench-core/bin"

echo ""
echo "--- Layer 5: cosbench-tomcat, cosbench-keystone, cosbench-httpauth, cosbench-swauth ---"
compile "cosbench-tomcat"   "$LOG:$CFG:$API:$HTTP:$CORE:$OSGI_CP"
compile "cosbench-keystone" "$LOG:$CFG:$API:$HTTP:$CORE:$OSGI_CP"
compile "cosbench-httpauth" "$LOG:$CFG:$API:$HTTP:$CORE:$OSGI_CP"
compile "cosbench-swauth"   "$LOG:$CFG:$API:$HTTP:$CORE:$OSGI_CP"

echo ""
echo "--- Layer 6: storage adapters ---"
S3_JARS=$(find "$DEV/cosbench-s3" -name "*.jar" | tr '\n' ':')
compile "cosbench-mock"    "$LOG:$CFG:$API:$HTTP:$CORE:$OSGI_CP"
compile "cosbench-s3"      "$LOG:$CFG:$API:$HTTP:$CORE:$OSGI_CP" "$S3_JARS"
compile "cosbench-swift"   "$LOG:$CFG:$API:$HTTP:$CORE:$OSGI_CP"
compile "cosbench-ampli"   "$LOG:$CFG:$API:$HTTP:$CORE:$OSGI_CP"
compile_optional "cosbench-openio"  "$LOG:$CFG:$API:$HTTP:$CORE:$OSGI_CP"
OSS_JARS=$(find "$DEV/cosbench-oss" -name "*.jar" | tr '\n' ':')
compile "cosbench-oss"     "$LOG:$CFG:$API:$HTTP:$CORE:$OSGI_CP" "$OSS_JARS"
ECS_JARS=$(find "$DEV/cosbench-ecs" -name "*.jar" | tr '\n' ':')
compile "cosbench-scality" "$LOG:$CFG:$API:$HTTP:$CORE:$OSGI_CP" "$ECS_JARS"
compile "cosbench-ecs"     "$LOG:$CFG:$API:$HTTP:$CORE:$OSGI_CP" "$ECS_JARS"

GCS_JARS=$(find "$DEV/cosbench-gcs" -name "*.jar" | tr '\n' ':')
compile "cosbench-gcs"     "$LOG:$CFG:$API:$HTTP:$CORE:$OSGI_CP" "$GCS_JARS"

compile_optional "cosbench-librados" "$LOG:$CFG:$API:$HTTP:$CORE:$OSGI_CP"

REDIS_JARS=$(find "$DEV/cosbench-redis" -name "*.jar" | tr '\n' ':')
compile "cosbench-redis"   "$LOG:$CFG:$API:$HTTP:$CORE:$OSGI_CP" "$REDIS_JARS"

COUCHBASE_JARS=$(find "$DEV/cosbench-couchbase" -name "*.jar" | tr '\n' ':')
compile "cosbench-couchbase" "$LOG:$CFG:$API:$HTTP:$CORE:$OSGI_CP" "$COUCHBASE_JARS"

VECTOR_JARS=$(find "$DEV/cosbench-vector" -name "*.jar" | tr '\n' ':')
compile "cosbench-vector"    "$LOG:$CFG:$API:$HTTP:$CORE:$OSGI_CP" "$VECTOR_JARS"

PGVECTOR_JARS=$(find "$DEV/cosbench-pgvector" -name "*.jar" | tr '\n' ':')
compile "cosbench-pgvector"  "$LOG:$CFG:$API:$HTTP:$CORE:$OSGI_CP" "$PGVECTOR_JARS"

echo ""
echo "--- Layer 7: cosbench-driver, cosbench-controller, cosbench-core-web ---"
KEYSTONE="$DEV/cosbench-keystone/bin"
HTTPAUTH="$DEV/cosbench-httpauth/bin"
SWAUTH="$DEV/cosbench-swauth/bin"
MOCK="$DEV/cosbench-mock/bin"
compile "cosbench-driver"     "$LOG:$CFG:$API:$HTTP:$CORE:$KEYSTONE:$HTTPAUTH:$SWAUTH:$MOCK:$OSGI_CP"
compile "cosbench-controller" "$LOG:$CFG:$API:$HTTP:$CORE:$OSGI_CP"
compile "cosbench-core-web"   "$LOG:$CFG:$API:$HTTP:$CORE:$OSGI_CP"
DRIVER="$DEV/cosbench-driver/bin"
CONTROLLER="$DEV/cosbench-controller/bin"
CORE_WEB="$DEV/cosbench-core-web/bin"

echo ""
echo "--- Layer 8: cosbench-driver-web, cosbench-controller-web ---"
compile "cosbench-driver-web"     "$LOG:$CFG:$API:$HTTP:$CORE:$DRIVER:$CORE_WEB:$OSGI_CP"
compile "cosbench-controller-web" "$LOG:$CFG:$API:$HTTP:$CORE:$CONTROLLER:$CORE_WEB:$OSGI_CP"

echo ""
echo "--- Assembling all bundles ---"
for NAME in cosbench-log cosbench-log4j cosbench-castor cosbench-config \
            cosbench-api cosbench-http cosbench-cdmi-util \
            cosbench-core cosbench-tomcat \
            cosbench-keystone cosbench-httpauth cosbench-swauth \
            cosbench-mock cosbench-s3 cosbench-swift cosbench-ampli \
            cosbench-oss cosbench-scality cosbench-ecs \
            cosbench-gcs cosbench-redis cosbench-couchbase cosbench-vector cosbench-pgvector \
            cosbench-driver cosbench-controller \
            cosbench-core-web cosbench-driver-web cosbench-controller-web; do
    echo -n "  $NAME ... "
    assemble "$NAME"
    echo "OK"
done

echo ""
echo "--- Copying plugins to release/ ---"
cp -r "$PLUGINS"/. "$ROOT/release/plugins/"
echo ""
echo "=== Build complete. Plugins in: $PLUGINS and release/plugins/ ==="
ls "$ROOT/release/plugins/"
