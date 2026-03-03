COSBench - Cloud Object Storage Benchmark
=========================================

COSBench is a benchmarking tool to measure the performance of Cloud Object Storage services. Object storage is an
emerging technology that is different from traditional file systems (e.g., NFS) or block device systems (e.g., iSCSI).
Amazon S3 and Openstack* swift are well-known object storage solutions.

COSBench now supports OpenStack* Swift, Amazon* S3, Amplidata v2.3, 2.5 and 3.1, Scality*, Ceph, CDMI, Google* Cloud Storage, Aliyun OSS as well as custom adaptors.


Fork additions (jowsiewski/cosbench — branch: feature/kv-backends)
-------------------------------------------------------------------

This fork extends the original COSBench with two additional storage adapters not present upstream:

### Redis / Google Cloud Memorystore (`type="redis"`)

Benchmarks key-value workloads against any Redis-compatible store:

- **Backends**: Redis CE, Redis Stack, Google Cloud Memorystore, Valkey, DragonflyDB
- **Operations**: SET (write), GET (read), DEL (delete), SCAN (list), key prefix scan (cleanup)
- **Auth**: password, TLS/SSL, logical database index
- **Sample workload**: `release/conf/redis-config-sample.xml`

```xml
<storage type="redis" config="host=127.0.0.1;port=6379;timeout=30000" />
```

### Couchbase (`type="couchbase"`)

Benchmarks key-value workloads against Couchbase Server:

- **SDK**: Couchbase Java SDK 3.x (Java 8 compatible)
- **Operations**: upsert (write), get (read), remove (delete)
- **Auth**: username/password, TLS
- **Sample workload**: `release/conf/couchbase-config-sample.xml`

```xml
<storage type="couchbase" config="host=127.0.0.1;bucket=default;username=Administrator;password=secret" />
```

### Vector Similarity Search (`type="vector"`)

Benchmarks vector search workloads using the RediSearch-compatible `FT.*` command set:

- **Backends**: Redis Stack, Redis 8+, Valkey + valkey-search module, DragonflyDB (v1.13+)
- **Index algorithms**: HNSW (approximate, fast) and FLAT (exact, brute-force)
- **Distance metrics**: COSINE, L2, IP (inner product)
- **Write**: `HSET key embedding <float32 binary blob>` — synthetic vectors generated from object name (no external data required)
- **Read**: `FT.SEARCH idx "*=>[KNN K @embedding $vec AS score]"` with DIALECT 2
- **Configurable**: vector dimensionality, K neighbours, HNSW M / EF_CONSTRUCTION
- **Sample workload**: `release/conf/vector-config-sample.xml`

```xml
<storage type="vector" config="host=127.0.0.1;port=6379;dim=128;algorithm=HNSW;distanceMetric=COSINE;knn=10" />
```

#### Vector adapter parameters

| Parameter | Default | Description |
|---|---|---|
| `host` | `localhost` | Server hostname or IP |
| `port` | `6379` | Server port |
| `password` | _(none)_ | AUTH password |
| `ssl` | `false` | Enable TLS |
| `indexName` | `cosbench-vec-idx` | RediSearch index name |
| `keyPrefix` | `vec:` | HASH key prefix |
| `vectorField` | `embedding` | HASH field holding the vector |
| `dim` | `128` | Vector dimensionality (float32 components) |
| `algorithm` | `HNSW` | `HNSW` or `FLAT` |
| `distanceMetric` | `COSINE` | `COSINE`, `L2`, or `IP` |
| `knn` | `10` | Neighbours returned per KNN search |
| `hnswM` | `16` | HNSW bi-directional links per node |
| `hnswEfConstruction` | `200` | HNSW candidate list size during build |
| `randomSeed` | `42` | Seed for reproducible synthetic vectors |

#### Prerequisites for vector benchmarking

| Backend | Setup |
|---|---|
| Redis Stack | `docker run -p 6379:6379 redis/redis-stack-server:latest` |
| Redis 8+ | No modules needed — native query engine built in |
| Valkey | Load `valkey-search` module: `--loadmodule /path/to/valkey-search.so` |
| DragonflyDB | `docker run -p 6379:6379 docker.dragonflydb.io/dragonflydb/dragonfly` (v1.13+) |

### pgvector — PostgreSQL Vector Extension (`type="pgvector"`)

Benchmarks vector similarity search workloads against PostgreSQL with the [pgvector](https://github.com/pgvector/pgvector) extension:

- **Index algorithms**: HNSW (pgvector 0.5.0+, approximate) and IVFFlat (exact partitioning)
- **Distance metrics**: cosine (`<=>`), L2 (`<->`), inner product (`<#>`)
- **Write**: `INSERT INTO table (id, embedding) VALUES (?, ?::vector)` — upsert on conflict
- **Read**: `SELECT id FROM table ORDER BY embedding <op> ?::vector LIMIT knn` (KNN search)
- **Configurable**: vector dimensionality, HNSW M / ef_construction, IVFFlat lists, query-time probes, K neighbours
- **Sample workload**: `release/conf/pgvector-config-sample.xml`

```xml
<storage type="pgvector" config="host=127.0.0.1;port=5432;database=postgres;username=postgres;password=cosbench;dim=128;indexType=hnsw;distanceMetric=cosine;knn=10" />
```

#### pgvector adapter parameters

| Parameter | Default | Description |
|---|---|---|
| `host` | `localhost` | PostgreSQL hostname or IP |
| `port` | `5432` | PostgreSQL port |
| `database` | `postgres` | Database name |
| `username` | `postgres` | DB user |
| `password` | _(empty)_ | DB password |
| `ssl` | `false` | Enable TLS |
| `timeout` | `30` | JDBC socket timeout (seconds, 0 = none) |
| `poolSize` | `4` | JDBC connection pool size per worker |
| `schema` | `public` | Schema holding the vector tables |
| `idColumn` | `id` | Primary-key column name |
| `vectorColumn` | `embedding` | Vector column name |
| `dim` | `128` | Vector dimensionality (float32 components) |
| `indexType` | `hnsw` | `hnsw` or `ivfflat` |
| `distanceMetric` | `cosine` | `cosine`, `l2`, or `ip` |
| `hnswM` | `16` | HNSW: max connections per layer |
| `hnswEfConstruction` | `64` | HNSW: candidate list size during build |
| `ivfflatLists` | `100` | IVFFlat: number of inverted lists |
| `probes` | `10` | Query-time probes (`hnsw.ef_search` or `ivfflat.probes`) |
| `knn` | `10` | Neighbours returned per KNN search |
| `randomSeed` | `42` | Seed for reproducible synthetic vectors |

#### Prerequisites for pgvector benchmarking

```bash
docker run -d --name pgvector-demo \
  -e POSTGRES_PASSWORD=cosbench \
  -p 5432:5432 \
  pgvector/pgvector:pg16
```


Important Notice and Contact Information
----------------------------------------

a) COSBench is not a product, and it does not have a full-time support team. Before you use this tool, please understand 
the need to invest enough effort to learn how to use it effectively and to address possible bugs.

b) To help COSBench develop further, please become an active member of the community and consider giving back by making
contributions.


Licensing
---------

a) Intel source code is being released under the Apache 2.0 license.

b) Additional libraries used with COSBench have their own licensing; refer to 3rd-party-licenses.pdf for details.


Distribution Packages
---------------------

Please refer to "DISTRIBUTIONS.md" to get the link for distribution packages.


Installation & Usage
--------------------

Please refer to "COSBenchUserGuide.pdf" for details.


Adaptor Development
-------------------
If needed, adaptors can be developed for new storage services; please refer to "COSBenchAdaptorDevGuide.pdf" for details.


Build
-----
If a build from source code is needed, please refer to BUILD.md for details.


Resources
---------

Wiki: (https://github.com/intel-cloud/cosbench/wiki)

Issue tracking: (https://github.com/intel-cloud/cosbench/issues)

Mailing list: (http://cosbench.1094679.n5.nabble.com/)


*Other names and brands may be claimed as the property of others.


Other related projects
----------------------
COSBench-Workload-Generator: (https://github.com/giteshnandre/COSBench-Workload-Generator)

COSBench-Plot: (https://github.com/icclab/cosbench-plot)

COSBench-Appliance: (https://susestudio.com/a/8Kp374/cosbench)

COSBench Ansible Playbook:

- (http://www.ksingh.co.in/blog/2016/05/29/deploy-cosbench-using-ansible/)
- (https://github.com/ksingh7/ansible-role-cosbench)
- (https://galaxy.ansible.com/ksingh7/cosbench/)


= END =
