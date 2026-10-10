# Binary Search Tree vs. Hash Table in Java

[Open the REPORT document](REPORT.docx)

This project implements an **unbalanced Binary Search Tree (BST)** and a **separate-chaining Hash Table** from scratch in **Java 17**, compares insertion, searching, and deletion, and measures both with **100, 1,000, and 10,000 elements**. Neither implementation delegates storage to Java's `HashSet`, `HashMap`, or `TreeSet`.

## Build and run

Install a **JDK 17 or later** and run these commands from the repository root. No Maven, Gradle, or external libraries are required.

```sh
javac -encoding UTF-8 -d out src/*.java test/*.java
java -cp out StructuresTest
java -cp out Benchmark
```

The benchmark regenerates `results/benchmark.json`, the measured-results section of this README, and the short report in `REPORT.txt` and `REPORT.docx`. Close the Word document before running it. The adversarial cases may take a minute or more; timings vary between runs. Keep any personal edits to the short report in a separate copy if you plan to regenerate it.

## Repository contents

- `src/IntSet.java`: common integer-set interface.
- `src/BST.java`: iterative BST implementation and height diagnostic.
- `src/HashTable.java`: linked-list buckets, collision handling, and resizing.
- `src/Benchmark.java`: deterministic workloads, warm-ups, repeated timings, and environment recording.
- `src/Json.java`: small JSON output helper using only the standard library.
- `src/ReportWriter.java`: generates result tables and the short report using Java's ZIP/XML format support.
- `test/StructuresTest.java`: ten correctness tests, with `HashSet` used only as a test oracle.
- `results/benchmark.json`: raw trials, medians, minimum/maximum times, normalized times, and structural diagnostics.
- `REPORT.docx`: approximately one-page explanation for the handwritten-report requirement.
- `REPORT.txt`: the same short explanation in plain text for easy reading and adaptation.
- `README.md`: full complexity analysis, methods, results, and conclusions.

## Implementation and operation analysis

Both structures store unique Java `int` keys, including negative values and `Integer.MIN_VALUE` / `Integer.MAX_VALUE`. `insert(int key)` returns true for a new key and false for a duplicate; `search(int key)` reports membership; `delete(int key)` returns true only when a key was removed. `size()` returns the number of keys. The type system restricts inputs to integers.

### Binary Search Tree

Every node has a key and left/right references. Keys smaller than a node belong to its left subtree and larger keys to its right subtree.

- **Insertion:** follow comparisons until a missing child is reached, then attach a leaf. Reject duplicates.
- **Search:** compare with the current node and follow the appropriate subtree until the key is found or the branch ends.
- **Deletion:** locate the key. Unlink a leaf, replace a one-child node with its child, or replace a two-child node's key with its in-order successor and unlink that successor.

All three operations use iterative loops, so a long chain does not overflow the call stack. No automatic balancing is performed. Their worst cost for a particular tree is proportional to its height, consistent with [Princeton's BST analysis](https://algs4.cs.princeton.edu/32bst/).

### Hash Table

The table starts with 16 buckets. The default hash is the integer itself, reduced modulo the bucket count and normalized for negative keys. Collisions are stored in singly linked lists. Before a new insertion would exceed a load factor of 0.75, the bucket array doubles and all existing nodes are redistributed. Deletion does not shrink the table.

- **Insertion:** compute a bucket, scan it for duplicates, resize if needed, then prepend a new node.
- **Search:** compute a bucket and compare keys along that bucket's chain.
- **Deletion:** find the node and reconnect its predecessor, or update the bucket head.

Rehashing relinks nodes directly instead of reinserting them through duplicate checks, keeping a resize linear even with collisions. Expected constant-time operations require a suitable key distribution and bounded load factor; see [Princeton's separate-chaining implementation notes](https://algs4.cs.princeton.edu/code/edu/princeton/cs/algs4/SeparateChainingHashST.java.html). This simple modulo hash is educational, not resistant to adversarial integer patterns.

## Time and space complexity

Let `n` be the current number of keys, `h` the BST height, `m` the number of hash buckets, and `alpha = n/m`. Hashing and comparing fixed-size integer keys are treated as constant-time operations. The table describes **one operation**, not a full batch.

| Structure | Operation | Best case | Expected / average case | Worst case |
|---|---|---|---|---|
| Unbalanced BST | Insert | O(1) | O(log n), random insertion order | O(n) |
| Unbalanced BST | Search | O(1) | O(log n), random tree / typical query | O(n) |
| Unbalanced BST | Delete | O(1) | O(log n), random tree / typical key | O(n) |
| Hash Table | Insert | O(1), without resize | O(1) amortized, well-distributed hashing | O(n) |
| Hash Table | Search | O(1) | O(1), well-distributed hashing | O(n) |
| Hash Table | Delete | O(1) | O(1), well-distributed hashing | O(n) |

**BST best cases:** a search can find the root immediately; insertion can add a root or use a vacant child of the root; deletion can remove a root with at most one child. Finding a root with two children does not necessarily make its deletion constant-time because locating the successor may require descending a subtree. For a balanced tree, a typical new-key insertion and the worst search/deletion path take O(log n). A plain BST does not guarantee that shape: ascending insertion produces height `n`, so an operation may traverse all keys. Random-input bounds are expectations, not guarantees for arbitrary insertion/deletion histories.

**Hash Table best cases:** an empty bucket or matching chain head permits constant work if insertion does not resize. Expected chain traversal is O(1 + alpha), which is O(1) when the load factor is bounded and hashing distributes keys suitably. Resizing alone takes O(n + m), which is O(n) when triggered by the default growth policy. A single insertion can therefore take O(n), even with good hashing. Geometric growth spreads resize costs across many insertions, giving expected amortized O(1) insertion. If all keys collide, duplicate checking, searching, and deletion can each scan a chain of length `n`, yielding O(n) worst-case time. Deletion is measured by key; it must first locate the node.

**Whole batches:** `n` typical operations take expected O(n log n) for a randomly built BST and O(n) for a well-distributed hash table, with amortization for insertion. Sorted BST construction and all-colliding hash construction take O(n²). Searching for all keys in a degenerate tree or one long chain also takes O(n²). Deleting all keys has an order-dependent total; the experiment uses a shuffled deletion order.

**Space:** the BST stores O(n) nodes, with O(1) auxiliary space for each implemented core operation. The Hash Table stores O(n + m); resizing temporarily uses an additional O(m) bucket array. Because it never shrinks, its bucket capacity can remain O(n_peak) after deletions, where `n_peak` is the maximum number of keys held. Diagnostic traversal is excluded from timings.

## Experimental method

Each trial starts from an empty structure and inserts `n` unique keys from `0` through `n - 1`. The same key orders are used for both structures within a trial.

1. **Random:** seeded shuffled insertion order with the default hash. The BST is usually shallow. These consecutive integers distribute especially well across the hash buckets; this is a favorable baseline, not a representative sample of all integer distributions.
2. **Sorted:** ascending insertion order with the same default hash. This isolates BST degeneration while retaining favorable hashing.
3. **Collisions:** shuffled insertion order, but a test-only hash function always returns zero. This deliberately forces the hash table's worst distribution while retaining the same BST workload as the random scenario.

For every structure, scenario, and size, the harness runs five discarded warm-up trials and seven measured trials with deterministic seeds. Execution order alternates between structures. Every measured trial times four separate batches:

- Insert all `n` keys, including any automatic rehashing.
- Search for all `n` present keys in shuffled order.
- Search for `n` absent keys from `n` through `2n - 1` in shuffled order.
- Delete all `n` present keys in shuffled order.

Missing keys are all above the largest stored key. This deliberately exercises the entire ascending BST chain in the sorted scenario; it is not a balanced mixture of different miss locations. Deletion timings describe a structure shrinking from `n` keys to zero, not `n` deletions at a fixed population.

Workload generation, construction of the initial empty structure, diagnostic traversal, correctness assertions, and file output are outside the timed intervals. The timed loops accumulate return values and verify them afterward so failed operations cannot silently produce convincing timings. Method calls, looping, allocations, garbage collection, and insertion-triggered resizing are included. The harness runs sequentially in one JVM process without forcing garbage collection or subtracting loop overhead.

Results below are **median elapsed milliseconds for an entire batch of `n` operations**. Dividing a batch time by `n` gives an average per-operation time within that batch, not a latency percentile. The JSON also preserves the minimum and maximum batch time across trials.

<!-- RESULTS:START -->
## Measured Java results

Recorded: **2026-10-10T21:04:18.874205300Z** (UTC).

- Runtime: Java 17.0.20.1 (Microsoft); OpenJDK 64-Bit Server VM.
- OS: Windows 11 10.0; amd64.
- CPU identifier: Not reported by environment; 16 available logical processors.
- Maximum JVM heap: 4002.0 MiB.
- JVM arguments: [].
- Timer: System.nanoTime(), converted to milliseconds.
- Five discarded warm-ups and seven measured trials per case; seed 20261011.

All values are **median milliseconds for a complete batch of n operations**. Decimal precision does not imply equivalent measurement accuracy.

### Random insertion with default hashing

| Elements | Structure | Insert (ms) | Search hit (ms) | Search miss (ms) | Delete (ms) |
|---:|---|---:|---:|---:|---:|
| 100 | BST | 0.012100 | 0.011900 | 0.006400 | 0.013100 |
| 100 | HashTable | 0.030700 | 0.005500 | 0.004900 | 0.012900 |
| 1000 | BST | 0.072100 | 0.064600 | 0.016900 | 0.117500 |
| 1000 | HashTable | 0.070500 | 0.008100 | 0.007300 | 0.016700 |
| 10000 | BST | 1.051600 | 0.990100 | 0.187800 | 1.064100 |
| 10000 | HashTable | 0.451500 | 0.084500 | 0.173600 | 0.102300 |

### Sorted insertion with default hashing

| Elements | Structure | Insert (ms) | Search hit (ms) | Search miss (ms) | Delete (ms) |
|---:|---|---:|---:|---:|---:|
| 100 | BST | 0.023200 | 0.023700 | 0.042200 | 0.014200 |
| 100 | HashTable | 0.004700 | 0.003100 | 0.002800 | 0.003100 |
| 1000 | BST | 2.031500 | 1.989800 | 3.966600 | 1.010200 |
| 1000 | HashTable | 0.040100 | 0.041900 | 0.005900 | 0.045900 |
| 10000 | BST | 211.337400 | 207.582300 | 420.262700 | 120.112000 |
| 10000 | HashTable | 0.163500 | 0.041700 | 0.129200 | 0.055400 |

### Random insertion with forced hash collisions

| Elements | Structure | Insert (ms) | Search hit (ms) | Search miss (ms) | Delete (ms) |
|---:|---|---:|---:|---:|---:|
| 100 | BST | 0.005300 | 0.004800 | 0.001500 | 0.006100 |
| 100 | HashTable | 0.082500 | 0.017200 | 0.031000 | 0.010500 |
| 1000 | BST | 0.068800 | 0.063200 | 0.016900 | 0.073400 |
| 1000 | HashTable | 1.693700 | 1.296400 | 2.591600 | 0.769700 |
| 10000 | BST | 0.958200 | 0.927400 | 0.122600 | 1.000900 |
| 10000 | HashTable | 140.035500 | 145.021800 | 288.030200 | 97.919600 |

### Comparison

- At 10,000 random keys, insertion took 1.051600 ms for the BST and 0.451500 ms for the Hash Table. The Hash Table was 2.33 times faster in this run.
- At 10,000 random keys, successful search took 0.990100 ms for the BST and 0.084500 ms for the Hash Table. The Hash Table was 11.72 times faster in this run.
- At 10,000 random keys, deletion took 1.064100 ms for the BST and 0.102300 ms for the Hash Table. The Hash Table was 10.40 times faster in this run.
- Sorted insertion at 10,000 keys took 211.337400 ms in the BST, compared with 1.051600 ms for random insertion.
- Forced collisions at 10,000 keys made Hash Table successful searches take 145.021800 ms, compared with 0.084500 ms with default hashing.

### Growth and structural checks

| Scenario | Structure | Insertion growth from 1,000 to 10,000 | Successful-search growth from 1,000 to 10,000 |
|---|---|---:|---:|
| random | BST | 14.59x | 15.33x |
| random | HashTable | 6.40x | 10.43x |
| sorted | BST | 104.03x | 104.32x |
| sorted | HashTable | 4.08x | 1.00x |
| collisions | BST | 13.93x | 14.67x |
| collisions | HashTable | 82.68x | 111.87x |

A tenfold increase in n suggests roughly 10x growth for linear batch work and 100x for quadratic batch work. These are qualitative expectations, not exact timing predictions.

- **random, 10,000 keys:** BST height 28-34 nodes; maximum observed hash chain 1; hash capacity 16384.
- **sorted, 10,000 keys:** BST height 10000-10000 nodes; maximum observed hash chain 1; hash capacity 16384.
- **collisions, 10,000 keys:** BST height 28-34 nodes; maximum observed hash chain 10000; hash capacity 16384.

The BST receives identical inputs in the random and collisions scenarios; any timing differences between those BST rows reflect runtime variation and run order. Small batches are especially sensitive to JIT compilation, scheduling, and timing overhead. Warm-ups and medians cannot eliminate these effects. These exploratory measurements are not a JMH study or proof of complexity.

All 126 measured trials and 72 operation summaries, including minima and maxima, are preserved in [results/benchmark.json](results/benchmark.json).

<!-- RESULTS:END -->

## Why performance differs in practice

Tree shape determines how many comparisons and pointer traversals a BST performs. Sorted input can turn the tree into a linked list, while random insertion usually gives much shorter paths. A self-balancing tree would avoid this particular worst case, but balancing adds implementation and update costs and is outside this assignment.

Hashing pays for hash computation and indexing, but a good distribution keeps chains short. Poor hashing increases the number of comparisons and dependent memory accesses. A low overall load factor cannot help if every key still maps to one bucket. Growth reduces normal chain lengths but occasionally causes a much slower insertion because existing entries must be redistributed.

Memory layout also matters. BST nodes and linked-list hash chains both require pointer chasing; the hash table additionally allocates a contiguous bucket array. Object allocation, cache behavior, branch prediction, and garbage collection influence constants even when asymptotic bounds are equal. These factors are plausible contributors, not separately measured causal explanations in this experiment.

For small batches, timer overhead, JVM JIT compilation, CPU scheduling, and normal runtime noise can dominate useful work. Warm-ups and medians reduce some variability but do not eliminate it. These measurements establish behavior for this implementation, machine, and workloads; they are not universal performance ratios or a formal proof of complexity.

## Conclusions

A well-distributed hash table is generally a strong choice for exact membership checks and updates, with expected constant-time operations. An ordinary BST offers key ordering and can support ordered traversal, predecessor/successor, and range queries, but its performance depends on shape. Those additional ordered-query operations are not benchmarked here.

The experiments include inputs favorable to hashing, inputs unfavorable to the unbalanced BST, and a deliberately broken hash function. Together they show why neither an unconditional "BST is always O(log n)" nor "Hash Table is always O(1)" is correct. Choose the structure based on the required operations, key distribution, memory budget, and whether worst-case guarantees are needed.

## Correctness validation

Run `java -cp out StructuresTest` after compilation. Ten tests cover empty structures, duplicates, negative and extreme `int` values, missing keys, reinsertion, BST root/leaf/one-child/two-child deletion, immediate and deeper successors, a successor with a right child, monotonic input, collision chains, repeated resizing, and invalid hash-table constructor arguments. For each of three configurations, 12,000 deterministic mixed operations are checked against a `HashSet` oracle, with additional checks that other keys survive updates. Tests use explicit checks and do not require the `-ea` option.
