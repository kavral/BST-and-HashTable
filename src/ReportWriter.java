import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Generates report outputs using only the Java standard library. */
final class ReportWriter {
    private ReportWriter() {}
    private static final String TITLE = "BST and Hash Table Performance Report";
    private static String f(double value) { return String.format(Locale.ROOT, "%.6f", value); }
    private static String shortTime(double value) { return String.format(Locale.ROOT, "%.3f", value); }
    private static double time(List<Benchmark.Summary> rows, String scenario, int n,
                               String structure, String operation) {
        return rows.stream().filter(row -> row.scenario().equals(scenario) && row.n() == n
                && row.structure().equals(structure) && row.operation().equals(operation))
                .findFirst().orElseThrow().median();
    }

    static void write(Map<String, Object> metadata, List<Benchmark.Trial> trials,
                      List<Benchmark.Summary> summary) throws IOException {
        StringBuilder section = new StringBuilder("## Measured Java results\n\n");
        section.append("Recorded: **").append(metadata.get("measured_at")).append("** (UTC).\n\n")
                .append("- Runtime: Java ").append(metadata.get("java_version")).append(" (")
                .append(metadata.get("java_vendor")).append("); ").append(metadata.get("vm_name")).append(".\n")
                .append("- OS: ").append(metadata.get("os")).append("; ").append(metadata.get("arch")).append(".\n")
                .append("- CPU identifier: ").append(metadata.get("cpu")).append("; ")
                .append(metadata.get("logical_processors")).append(" available logical processors.\n")
                .append("- Maximum JVM heap: ").append(metadata.get("max_heap_mib")).append(" MiB.\n")
                .append("- JVM arguments: ").append(metadata.get("vm_arguments")).append(".\n")
                .append("- Timer: System.nanoTime(), converted to milliseconds.\n")
                .append("- Five discarded warm-ups and seven measured trials per case; seed ")
                .append(Benchmark.BASE_SEED).append(".\n\n")
                .append("All values are **median milliseconds for a complete batch of n operations**. ")
                .append("Decimal precision does not imply equivalent measurement accuracy.\n\n");
        for (String scenario : Benchmark.SCENARIOS) {
            String title = switch (scenario) {
                case "random" -> "Random insertion with default hashing";
                case "sorted" -> "Sorted insertion with default hashing";
                default -> "Random insertion with forced hash collisions";
            };
            section.append("### ").append(title).append("\n\n")
                    .append("| Elements | Structure | Insert (ms) | Search hit (ms) | Search miss (ms) | Delete (ms) |\n")
                    .append("|---:|---|---:|---:|---:|---:|\n");
            for (int n : Benchmark.SIZES) for (String structure : Benchmark.STRUCTURES) {
                section.append("| ").append(n).append(" | ").append(structure).append(" | ");
                for (String operation : Benchmark.OPERATIONS)
                    section.append(f(time(summary, scenario, n, structure, operation))).append(" | ");
                section.append('\n');
            }
            section.append('\n');
        }
        section.append("### Comparison\n\n");
        String[] labels = {"insertion", "successful search", "deletion"};
        String[] operations = {"insert", "search_hit", "delete"};
        for (int index = 0; index < operations.length; index++) {
            double bst = time(summary, "random", 10000, "BST", operations[index]);
            double hash = time(summary, "random", 10000, "HashTable", operations[index]);
            section.append("- At 10,000 random keys, ").append(labels[index]).append(" took ")
                    .append(f(bst)).append(" ms for the BST and ").append(f(hash)).append(" ms for the Hash Table. ")
                    .append(bst >= hash ? "The Hash Table" : "The BST").append(" was ")
                    .append(String.format(Locale.ROOT, "%.2f", Math.max(bst, hash) / Math.min(bst, hash)))
                    .append(" times faster in this run.\n");
        }
        section.append("- Sorted insertion at 10,000 keys took ")
                .append(f(time(summary, "sorted", 10000, "BST", "insert"))).append(" ms in the BST, compared with ")
                .append(f(time(summary, "random", 10000, "BST", "insert"))).append(" ms for random insertion.\n")
                .append("- Forced collisions at 10,000 keys made Hash Table successful searches take ")
                .append(f(time(summary, "collisions", 10000, "HashTable", "search_hit"))).append(" ms, compared with ")
                .append(f(time(summary, "random", 10000, "HashTable", "search_hit"))).append(" ms with default hashing.\n\n")
                .append("### Growth and structural checks\n\n")
                .append("| Scenario | Structure | Insertion growth from 1,000 to 10,000 | Successful-search growth from 1,000 to 10,000 |\n")
                .append("|---|---|---:|---:|\n");
        for (String scenario : Benchmark.SCENARIOS) for (String structure : Benchmark.STRUCTURES) {
            section.append("| ").append(scenario).append(" | ").append(structure).append(" | ");
            for (String operation : new String[]{"insert", "search_hit"})
                section.append(String.format(Locale.ROOT, "%.2f",
                        time(summary, scenario, 10000, structure, operation)
                                / time(summary, scenario, 1000, structure, operation))).append("x | ");
            section.append('\n');
        }
        section.append("\nA tenfold increase in n suggests roughly 10x growth for linear batch work and ")
                .append("100x for quadratic batch work. These are qualitative expectations, not exact timing predictions.\n\n");
        for (String scenario : Benchmark.SCENARIOS) {
            int minimumHeight = Integer.MAX_VALUE, maximumHeight = 0, maximumChain = 0, capacity = 0;
            for (Benchmark.Trial trial : trials) {
                if (trial.n() != 10000 || !trial.scenario().equals(scenario)) continue;
                if (trial.structure().equals("BST")) {
                    minimumHeight = Math.min(minimumHeight, trial.height());
                    maximumHeight = Math.max(maximumHeight, trial.height());
                } else {
                    maximumChain = Math.max(maximumChain, trial.maximumChain());
                    capacity = trial.capacity();
                }
            }
            section.append("- **").append(scenario).append(", 10,000 keys:** BST height ")
                    .append(minimumHeight).append("-").append(maximumHeight).append(" nodes; maximum observed hash chain ")
                    .append(maximumChain).append("; hash capacity ").append(capacity).append(".\n");
        }
        section.append("\nThe BST receives identical inputs in the random and collisions scenarios; any timing differences ")
                .append("between those BST rows reflect runtime variation and run order. Small batches are especially ")
                .append("sensitive to JIT compilation, scheduling, and timing overhead. Warm-ups and medians cannot eliminate ")
                .append("these effects. These exploratory measurements are not a JMH study or proof of complexity.\n\n")
                .append("All 126 measured trials and 72 operation summaries, including minima and maxima, are preserved ")
                .append("in [results/benchmark.json](results/benchmark.json).\n");
        Path readme = Path.of("README.md");
        String original = Files.readString(readme, StandardCharsets.UTF_8);
        String startMarker = "<!-- RESULTS:START -->", endMarker = "<!-- RESULTS:END -->";
        int start = original.indexOf(startMarker), end = original.indexOf(endMarker);
        if (start < 0 || end < start) throw new IOException("README result markers are missing");
        Files.writeString(readme, original.substring(0, start + startMarker.length()) + "\n"
                + section.toString().replace(" | \n", " |\n") + "\n" + original.substring(end), StandardCharsets.UTF_8);
        List<String> paragraphs = shortReport(summary);
        Files.writeString(Path.of("REPORT.txt"), TITLE + "\n\n" + String.join("\n\n", paragraphs) + "\n",
                StandardCharsets.UTF_8);
        writeDocx(paragraphs);
    }

    private static List<String> shortReport(List<Benchmark.Summary> summary) {
        return List.of(
            "I implemented a Binary Search Tree (BST) and a Hash Table in Java to compare insertion, searching, "
            + "and deletion. The BST stores smaller keys on the left and larger keys on the right, without balancing "
            + "itself. The Hash Table uses linked lists for collisions and doubles its capacity before its load factor exceeds 0.75.",
            "For the BST, all three operations have a best-case time of O(1) when only constant work near the root "
            + "is needed. With random insertion, their expected time is O(log n). Sorted insertion creates a chain, "
            + "so an operation can take O(n) in the worst case.",
            "For the Hash Table, all three operations have a best case of O(1). With well-distributed keys, searching "
            + "and deletion take expected O(1), while insertion takes expected amortized O(1). Amortized means that "
            + "occasional resizing costs are spread across many insertions. All operations can take O(n) if keys collide; "
            + "insertion can also take O(n) during resizing.",
            "I tested 100, 1,000, and 10,000 elements using System.nanoTime(), five warm-up trials, and seven measured "
            + "trials per case. For 10,000 random keys, median BST insertion, successful searching, and deletion took "
            + shortTime(time(summary, "random", 10000, "BST", "insert")) + " ms, "
            + shortTime(time(summary, "random", 10000, "BST", "search_hit")) + " ms, and "
            + shortTime(time(summary, "random", 10000, "BST", "delete")) + " ms. Hash Table times were "
            + shortTime(time(summary, "random", 10000, "HashTable", "insert")) + " ms, "
            + shortTime(time(summary, "random", 10000, "HashTable", "search_hit")) + " ms, and "
            + shortTime(time(summary, "random", 10000, "HashTable", "delete"))
            + " ms, respectively. Each time covers a whole batch of 10,000 operations.",
            "Sorted input made BST insertion take "
            + shortTime(time(summary, "sorted", 10000, "BST", "insert"))
            + " ms at 10,000 keys. Forcing all hash keys into one bucket made successful searching take "
            + shortTime(time(summary, "collisions", 10000, "HashTable", "search_hit"))
            + " ms. These cases show how tree shape and collisions can damage performance.",
            "I concluded that performance depends on key order, hash distribution, resizing, memory access, and JVM "
            + "overhead. Small tests are especially sensitive to noise. A Hash Table is usually suitable for exact lookups. "
            + "A BST is useful when ordering matters, but balancing is needed to guarantee logarithmic worst-case operations."
        );
    }

    private static String escapeXml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Minimal interoperable OOXML, generated with Java's ZIP API. */
    private static void writeDocx(List<String> paragraphs) throws IOException {
        StringBuilder document = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                <w:p><w:pPr><w:pStyle w:val="Title"/><w:jc w:val="center"/>
                <w:spacing w:after="200"/></w:pPr><w:r><w:t>""");
        document.append(TITLE).append("</w:t></w:r></w:p>");
        for (String paragraph : paragraphs) {
            document.append("<w:p><w:pPr><w:spacing w:after=\"120\" w:line=\"240\" w:lineRule=\"auto\"/>")
                    .append("<w:ind w:firstLine=\"360\"/></w:pPr><w:r><w:t>")
                    .append(escapeXml(paragraph)).append("</w:t></w:r></w:p>");
        }
        document.append("""
                <w:sectPr><w:pgSz w:w="12240" w:h="15840"/>
                <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"
                w:header="720" w:footer="720" w:gutter="0"/></w:sectPr></w:body></w:document>
                """);
        String styles = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman"/>
                <w:sz w:val="24"/><w:color w:val="000000"/><w:lang w:val="en-US"/></w:rPr></w:rPrDefault></w:docDefaults>
                <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
                <w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/>
                <w:basedOn w:val="Normal"/><w:pPr><w:keepNext/></w:pPr><w:rPr>
                <w:b/><w:sz w:val="30"/><w:color w:val="000000"/></w:rPr></w:style></w:styles>
                """;
        String types = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
                </Types>
                """;
        String relationships = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
                """;
        String documentRelationships = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>
                """;
        Path temporary = Path.of("REPORT.docx.tmp");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
            for (var entry : Map.of("[Content_Types].xml", types, "_rels/.rels", relationships,
                    "word/document.xml", document.toString(), "word/styles.xml", styles,
                    "word/_rels/document.xml.rels", documentRelationships).entrySet()) {
                ZipEntry item = new ZipEntry(entry.getKey());
                item.setTime(0);
                zip.putNextEntry(item);
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        Files.move(temporary, Path.of("REPORT.docx"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
