# KeyDB Large-Matrix Writes: Session Notes

## 1. Context

**Question 1:** What is KeyDB, and is it similar to Redis?

**Situation:** A Spring Boot service writes a `List<List<Double>>` (1500 x 1500 = 2.25M boxed doubles) into a KeyDB hash with `redisTemplate.opsForHash().put(key, hashKey, value)`. The call happens about 15,000 times. The writing method lives in a separate cache microservice and is `@Async("redisExecutor")`.

**Problems:**
1. How to streamline these heavy writes.
2. About 1800 entries are not visible in KeyDB when browsing with a Redis desktop manager.

**Preference noted during the session:** do not use `ByteBuffer`.

---

## 2. KeyDB vs Redis (summary)

KeyDB is an open-source in-memory data store forked from Redis. It is wire-compatible (RESP), so Lettuce, Jedis and Spring Data Redis work unchanged.

| | Redis | KeyDB |
|---|---|---|
| Protocol | RESP | RESP (compatible) |
| Execution | Mostly single-threaded (optional I/O threads) | Multi-threaded |
| Multi-master | No | Yes (active-active, last-write-wins style conflicts) |
| Ecosystem/modules | Largest | Smaller |

Trade-offs:
- KeyDB can lag behind newer Redis releases (new commands or modules may be missing).
- Redis changed its licensing in 2024, which pushed interest toward Valkey, KeyDB and Dragonfly. Valkey (Linux Foundation fork) is generally the more actively maintained drop-in. Verify KeyDB's current repo activity before committing to it.
- Multithreading helps most when CPU-bound on one node, less when network- or memory-bound.

---

## 3. Root-cause analysis

A 1500 x 1500 `List<List<Double>>` is heavy:
- 2.25M `Double` objects, roughly 40-50 MB of heap per matrix.
- With the default JDK serializer or JSON, roughly 20-40 MB per value on the wire.
- At about 15,000 calls, that is hundreds of GB through KeyDB. This causes network saturation, memory pressure, blocked commands and likely eviction.

Findings from the code review:
1. **`try/catch` cannot catch Redis failures.** `saveData` is `@Async void`, so exceptions (timeouts, serialization errors) never reach the caller's `catch`. Failed writes vanish silently, which fits the missing entries.
2. **Queue capacity of 10000 is a memory bomb.** Each queued task holds a full matrix on the heap. Also, `ThreadPoolTaskExecutor` only grows past `corePoolSize` once the queue is full, so `maxPoolSize(20)` never applies.
3. **The virtual thread executor was dead code.** `Executors.newVirtualThreadPerTaskExecutor()` was created and never used or shut down.
4. **Possible double serialization:** if `keydb.saveData` is a REST/Feign call to another service, the matrix is serialized to JSON for HTTP and again for KeyDB. If it is a shared library in the same JVM, ignore this. (Open question: which is it?)

---

## 4. Threads vs virtual threads

- Threads help with CPU work (serialize/compress) and overlapping network round trips.
- They do not help if bandwidth-bound. With Lettuce, all threads share one multiplexed connection.
- Virtual threads need Java 21+ (Spring Boot 3.2+: `spring.threads.virtual.enabled=true`). They do not cap concurrency by themselves. 15,000 virtual threads at once would cause an OutOfMemoryError. A cap (a `Semaphore` or a fixed pool) is still required.
- A fixed pool of 4-8 threads is the safer choice. Measure and tune.

---

## 5. Problem 1 solutions: slow, heavy writes

**A. Compact format + compression (best effort-to-gain ratio)**
- Use `double[][]` instead of `List<List<Double>>`.
- Serialize with Kryo (or Protobuf), then compress with LZ4.
- Use a byte-array/custom `hashValueSerializer` so Spring does not re-serialize.
- Cost: a custom serializer and a read-side change.

**B. Keep KeyDB small, put the blob in S3**
- Store the matrix in S3 and only a pointer plus metadata (rows, cols, version) in the hash.
- Best when writes are frequent but reads are rare or bulk. Cost: an extra S3 hop on reads.

**C. Chunk + bounded parallel writes**
- Split into row blocks (50-100 rows, about 0.5-1 MB each) as separate hash fields (`sId:0`, `sId:1`, ...).
- Write through a fixed pool with a small queue and `CallerRunsPolicy`.
- Cost: reassembly on read, and a completeness marker (metadata field written last).

**Recommendation:** A first, add C if timeouts persist, B only if the read pattern allows. Also question whether all 15,000 writes are needed (skip unchanged matrices).

---

## 6. Problem 2 solutions: about 1800 entries not visible

**A. Make failures visible (most likely cause)**
- Return `CompletableFuture` from the async method, log failures with the key, and collect results.
- Compare attempted 15,000 vs `HLEN` in KeyDB.

**B. Check eviction and memory on the server**
- `INFO memory`, `INFO stats` (look at `evicted_keys`), `CONFIG GET maxmemory-policy`.
- With `maxmemory` reached and an `allkeys-*`/`volatile-*` policy, old entries are silently deleted.
- Fixes: raise `maxmemory`, use `noeviction` so writes fail loudly, shrink the payload.

**C. Rule out key/display issues**
- Use `StringRedisSerializer` for key and hash key so keys are readable and match GUI filters.
- Confirm the GUI is on the same DB index and node. Check `HLEN <key>`, `DBSIZE`, `TTL`.
- Check whether the same `sId` repeats (overwrites mean fewer distinct entries).
- GUI tools often cap SCAN results or refuse to render huge values.

Useful CLI checks (`redis-cli` / `keydb-cli`):

```
DBSIZE
INFO keyspace
HLEN <key>
MEMORY USAGE <key>
INFO stats | grep evicted
CONFIG GET maxmemory*
--bigkeys
```

**Suggested order:**
1. Add `CompletableFuture` + logging.
2. Check `INFO stats` / `evicted_keys`.
3. Shrink the payload with `double[][]` + Kryo + LZ4.
4. Fix the executor (small queue, 8 threads).
5. Chunk or move to S3 if still needed.

---

## 7. Serialization notes (why not raw bytes, alternatives)

`ByteBuffer` would simply pack each `double` into 8 raw bytes with no per-element overhead. The bytes carry no description of themselves, so rows, cols, type and byte order must be known on read. Since `ByteBuffer` was ruled out, alternatives are:

- **Kryo** (used below): least code, no schema, JVM-only. Kryo instances are not thread-safe, so pool them.
- **Protobuf:** `repeated double` is packed; a schema carries rows/cols and is versionable and cross-language. Needs a code-generation build step.

```proto
message Matrix {
  int32 rows = 1;
  int32 cols = 2;
  repeated double values = 3;   // flat, row-major
}
```

- **`DataOutputStream`:** zero dependencies, slower for 2.25M values.

---

## 8. Final code: Kryo + LZ4

### Dependencies

```xml
<dependency>
    <groupId>com.esotericsoftware</groupId>
    <artifactId>kryo</artifactId>
    <version>5.6.0</version>
</dependency>
<dependency>
    <groupId>org.lz4</groupId>
    <artifactId>lz4-java</artifactId>
    <version>1.8.0</version>
</dependency>
```

Check for newer versions before pinning. `org.lz4:lz4-java` 1.8.0 has, I believe, had a security advisory, and a maintained fork is published as `at.yawk.lz4:lz4-java` with the same package names.

### 8.1 Serializer (Kryo + LZ4)

```java
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class MatrixSerializer implements RedisSerializer<double[][]> {

    private static final int BUFFER = 64 * 1024;

    // Kryo is not thread-safe, so pool instances (needed with the async executor)
    private static final Pool<Kryo> KRYO_POOL = new Pool<>(true, false, 16) {
        @Override
        protected Kryo create() {
            Kryo kryo = new Kryo();
            kryo.setRegistrationRequired(true);
            kryo.register(double[][].class);
            kryo.register(double[].class);
            return kryo;
        }
    };

    @Override
    public byte[] serialize(double[][] matrix) {
        if (matrix == null) return null;
        Kryo kryo = KRYO_POOL.obtain();
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(BUFFER);
            // closing Output closes the LZ4 stream, which writes the final block
            try (Output out = new Output(new LZ4BlockOutputStream(bos), BUFFER)) {
                kryo.writeObject(out, matrix);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new SerializationException("Matrix serialization failed", e);
        } finally {
            KRYO_POOL.free(kryo);
        }
    }

    @Override
    public double[][] deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        Kryo kryo = KRYO_POOL.obtain();
        try (Input in = new Input(new LZ4BlockInputStream(new ByteArrayInputStream(bytes)), BUFFER)) {
            return kryo.readObject(in, double[][].class);
        } catch (IOException e) {
            throw new SerializationException("Matrix deserialization failed", e);
        } finally {
            KRYO_POOL.free(kryo);
        }
    }
}
```

### 8.2 Dedicated template and executor config

Use a separate template for matrices. Changing the serializer on a shared template would break other values stored through it.

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class RedisConfig {

    @Bean
    public RedisTemplate<String, double[][]> matrixRedisTemplate(RedisConnectionFactory cf) {
        RedisTemplate<String, double[][]> template = new RedisTemplate<>();
        template.setConnectionFactory(cf);

        StringRedisSerializer str = new StringRedisSerializer();
        template.setKeySerializer(str);
        template.setHashKeySerializer(str);                      // readable keys in your GUI
        template.setHashValueSerializer(new MatrixSerializer()); // Kryo + LZ4
        template.afterPropertiesSet();
        return template;
    }

    @Bean("redisExecutor")
    public ThreadPoolTaskExecutor redisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(16); // small queue, so memory stays bounded
        executor.setThreadNamePrefix("redis-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

### 8.3 Cache service (write and read)

```java
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class MatrixCacheService {

    private final RedisTemplate<String, double[][]> matrixRedisTemplate;

    public MatrixCacheService(RedisTemplate<String, double[][]> matrixRedisTemplate) {
        this.matrixRedisTemplate = matrixRedisTemplate;
    }

    @Async("redisExecutor")
    public CompletableFuture<Void> saveMatrix(String hashName, String key, double[][] matrix) {
        long t1 = System.currentTimeMillis();
        try {
            matrixRedisTemplate.opsForHash().put(hashName, key, matrix);
            log.info("saveMatrix {}:{} took {} ms", hashName, key, System.currentTimeMillis() - t1);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("saveMatrix failed {}:{}", hashName, key, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    public double[][] getMatrix(String hashName, String key) {
        return (double[][]) matrixRedisTemplate.opsForHash().get(hashName, key);
    }
}
```

### 8.4 Caller side

```java
try {
    double[][] transLoss = buildTransLoss();   // better: build as double[][] from the start
    futures.add(
        matrixCache.saveMatrix(KeyDBConstants.SONAR_TL_MAP + gameId,
                               String.valueOf(sonarLogBean.getsId()),
                               transLoss)
    );
} catch (Exception e) {
    log.error("TL build failed", e);
}

// after the batch finishes, surface failures
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
```

Stopgap converter if `transLoss` cannot yet be built as `double[][]`:

```java
static double[][] toArray(List<List<Double>> list) {
    double[][] result = new double[list.size()][];
    for (int i = 0; i < list.size(); i++) {
        List<Double> row = list.get(i);
        double[] r = new double[row.size()];
        for (int j = 0; j < r.length; j++) r[j] = row.get(j);
        result[i] = r;
    }
    return result;
}
```

### 8.5 Original code (for reference)

Caller:

```java
try{
    List<List<Double>> transLoss = new ArrayList<List<Double>>();
    //the logic of creating new transLoss happens and gets stored into keydb
    keydb.saveData(KeyDBConstants.SONAR_TL_MAP+gameId, sonarLogBean.getsId(), transLoss);
}catch (Exception e) {
    System.out.printf("Printing stack");
    e.printStackTrace();
}
```

Original async method:

```java
@Async("redisExecutor")
public void saveData(String hashMapName, Object key, Object value) {
    long t1 = System.currentTimeMillis();
    redisTemplate.opsForHash().put(hashMapName, key, value);
    long t2 = System.currentTimeMillis();
    System.out.println("Time taken individually "+ (t2-t1));
}
```

Original executor config:

```java
@Bean("redisExecutor")
public ThreadPoolTaskExecutor redisExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    ExecutorService exe = Executors.newVirtualThreadPerTaskExecutor(); // unused

    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(10000);
    executor.setThreadNamePrefix("redis-worker-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
}
```

---

## 9. Caveats and migration notes

- **Old data:** entries already in KeyDB were written with the old serializer and cannot be read by `MatrixSerializer`. Use a new hash name (e.g. `SONAR_TL_MAP_V2`) or flush the old ones.
- **`allOf().join()`:** with thousands of futures, `join()` throws on the first failure. To count failures, use `future.handle(...)` or check `isCompletedExceptionally()` after `allOf` completes.
- **Timeouts:** with multi-MB values, raise the Lettuce command timeout (`spring.data.redis.timeout`).
- **Chunking:** if still hitting timeouts, split matrices into row blocks (see Problem 1, Solution C).
- **Virtual threads (optional):** use `SimpleAsyncTaskExecutor` with `setVirtualThreads(true)` and `setConcurrencyLimit(8)` rather than an unbounded virtual-thread executor.
- **Logging:** replace `System.out.println` / `printStackTrace` with a logger.

## 10. Open questions

1. Is `keydb.saveData` a REST/Feign call to another service, or a shared library in the same JVM?
2. What do `HLEN <key>`, `INFO memory`, `evicted_keys` and `maxmemory-policy` show on the KeyDB instance?
3. Is KeyDB running as a single node, active-active, or clustered? Is the GUI pointed at the same node and DB index?
4. Are all 15,000 writes necessary, or can unchanged matrices be skipped?
5. Do other languages or services need to read these matrices (Kryo vs Protobuf)?