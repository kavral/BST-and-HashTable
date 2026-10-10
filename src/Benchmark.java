import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Reproducible, single-process educational benchmark, without dependencies. */
public final class Benchmark {
    static final int[] SIZES = {100, 1000, 10000};
    static final String[] SCENARIOS = {"random", "sorted", "collisions"};
    static final String[] STRUCTURES = {"BST", "HashTable"};
    static final String[] OPERATIONS = {"insert", "search_hit", "search_miss", "delete"};
    static final int WARMUPS = 5;
    static final int REPETITIONS = 7;
    static final long BASE_SEED = 20261011L;
    private static volatile long blackhole;

    record Trial(String scenario, int n, int trial, long seed, String structure,
                 double[] times, int height, int capacity, int maximumChain, double loadFactor) {}
    record Summary(String scenario, int n, String structure, String operation,
                   double median, double minimum, double maximum, double microsecondsPerOperation) {}

    private static int[] shuffled(int[] values, long seed) {
        int[] copy = values.clone();
        Random random = new Random(seed);
        for (int i = copy.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temporary = copy[i]; copy[i] = copy[j]; copy[j] = temporary;
        }
        return copy;
    }

    private static double measure(IntSet set, int operation, int[] keys, int expectedCount) {
        long count = 0;
        long start = System.nanoTime();
        switch (operation) {
            case 0 -> { for (int key : keys) if (set.insert(key)) count++; }
            case 1, 2 -> { for (int key : keys) if (set.search(key)) count++; }
            case 3 -> { for (int key : keys) if (set.delete(key)) count++; }
            default -> throw new IllegalArgumentException("Unknown operation");
        }
        long elapsed = System.nanoTime() - start;
        blackhole = count; // Consume results outside the timed region.
        if (count != expectedCount) throw new AssertionError("Incorrect operation results");
        return elapsed / 1_000_000.0;
    }

    private static Trial run(String scenario, int n, int trial, long seed, String structure,
                             int[] insertion, int[] hits, int[] misses, int[] deletion) {
        IntSet set = structure.equals("BST") ? new BST()
                : scenario.equals("collisions") ? new HashTable(16, key -> 0) : new HashTable();
        double[] times = new double[4];
        times[0] = measure(set, 0, insertion, n);
        if (set.size() != n) throw new AssertionError("Incorrect size after insertion");
        times[1] = measure(set, 1, hits, n);
        times[2] = measure(set, 2, misses, 0);
        int height = 0, capacity = 0, maximumChain = 0;
        double loadFactor = 0;
        // All diagnostic traversal is outside the timed regions.
        if (set instanceof BST tree) height = tree.height();
        if (set instanceof HashTable table) {
            capacity = table.capacity();
            maximumChain = table.maxChainLength();
            loadFactor = table.loadFactor();
        }
        times[3] = measure(set, 3, deletion, n);
        if (set.size() != 0) throw new AssertionError("Incorrect size after deletion");
        return new Trial(scenario, n, trial, seed, structure, times,
                height, capacity, maximumChain, loadFactor);
    }

    private static List<Summary> summarize(List<Trial> trials) {
        List<Summary> result = new ArrayList<>();
        for (String scenario : SCENARIOS) for (int n : SIZES)
            for (String structure : STRUCTURES) for (int operation = 0; operation < 4; operation++) {
                List<Double> values = new ArrayList<>();
                for (Trial trial : trials)
                    if (trial.scenario().equals(scenario) && trial.n() == n
                            && trial.structure().equals(structure))
                        values.add(trial.times()[operation]);
                values.sort(Double::compare);
                if (values.size() != REPETITIONS) throw new AssertionError("Missing trials");
                double median = values.get(values.size() / 2);
                result.add(new Summary(scenario, n, structure, OPERATIONS[operation], median,
                        values.get(0), values.get(values.size() - 1), median * 1000 / n));
            }
        return result;
    }

    private static Map<String, Object> metadata() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("measured_at", Instant.now().toString());
        data.put("language", "Java");
        data.put("java_version", System.getProperty("java.version"));
        data.put("java_vendor", System.getProperty("java.vendor"));
        data.put("vm_name", System.getProperty("java.vm.name"));
        data.put("vm_arguments", ManagementFactory.getRuntimeMXBean().getInputArguments());
        data.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        data.put("arch", System.getProperty("os.arch"));
        String cpu = System.getenv("PROCESSOR_IDENTIFIER");
        data.put("cpu", cpu == null ? "Not reported by environment" : cpu);
        data.put("logical_processors", Runtime.getRuntime().availableProcessors());
        data.put("max_heap_mib", Runtime.getRuntime().maxMemory() / (1024.0 * 1024));
        data.put("timer", "System.nanoTime(), converted to milliseconds");
        data.put("sizes", Arrays.stream(SIZES).boxed().toList());
        data.put("warmups_per_case", WARMUPS);
        data.put("repetitions", REPETITIONS);
        data.put("base_seed", BASE_SEED);
        data.put("note", "Sequential single-JVM run, default JIT and GC; no forced GC. "
                + "Times include loops, method calls, allocations, and insertion-triggered resizing. "
                + "Constructors, input generation, diagnostics, validation, and file I/O are excluded.");
        return data;
    }

    private static void writeJson(Map<String, Object> metadata, List<Trial> trials,
                                  List<Summary> summary) throws IOException {
        List<Object> raw = new ArrayList<>();
        for (Trial trial : trials) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("scenario", trial.scenario()); row.put("n", trial.n());
            row.put("trial", trial.trial()); row.put("seed", trial.seed());
            row.put("structure", trial.structure());
            Map<String, Object> times = new LinkedHashMap<>();
            for (int op = 0; op < 4; op++) times.put(OPERATIONS[op], trial.times()[op]);
            row.put("times_ms", times);
            row.put("diagnostics", trial.structure().equals("BST")
                    ? Map.of("height_nodes", trial.height())
                    : Map.of("capacity", trial.capacity(), "maximum_chain", trial.maximumChain(),
                             "load_factor", trial.loadFactor()));
            raw.add(row);
        }
        List<Object> summaries = new ArrayList<>();
        for (Summary value : summary) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("scenario", value.scenario()); row.put("n", value.n());
            row.put("structure", value.structure()); row.put("operation", value.operation());
            row.put("median_ms", value.median()); row.put("min_ms", value.minimum());
            row.put("max_ms", value.maximum());
            row.put("median_us_per_operation", value.microsecondsPerOperation());
            summaries.add(row);
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("metadata", metadata); output.put("summary", summaries); output.put("raw", raw);
        Files.createDirectories(Path.of("results"));
        Files.writeString(Path.of("results/benchmark.json"), Json.stringify(output) + "\n",
                StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        if (!Files.isRegularFile(Path.of("README.md")))
            throw new IllegalStateException("Run from the repository root");
        List<Trial> trials = new ArrayList<>();
        for (String scenario : SCENARIOS) for (int n : SIZES) {
            int[] keys = new int[n];
            int[] absent = new int[n];
            for (int i = 0; i < n; i++) { keys[i] = i; absent[i] = i + n; }
            for (int trial = -WARMUPS; trial < REPETITIONS; trial++) {
                long seed = BASE_SEED + n + (trial + WARMUPS) * 1009L;
                int[] insertion = scenario.equals("sorted") ? keys : shuffled(keys, seed);
                int[] hits = shuffled(keys, seed + 1);
                int[] misses = shuffled(absent, seed + 2);
                int[] deletion = shuffled(keys, seed + 3);
                int first = (trial + WARMUPS) % 2;
                for (int position = 0; position < 2; position++) {
                    String structure = STRUCTURES[(first + position) % 2];
                    Trial result = run(scenario, n, trial + 1, seed, structure,
                            insertion, hits, misses, deletion);
                    if (trial >= 0) trials.add(result);
                }
            }
            System.out.println("Completed " + scenario + ", n=" + n);
        }
        List<Summary> summary = summarize(trials);
        Map<String, Object> metadata = metadata();
        writeJson(metadata, trials, summary);
        ReportWriter.write(metadata, trials, summary);
        System.out.println("Saved Java measurements, updated README.md, REPORT.txt and REPORT.docx");
    }
}
