import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

/** Dependency-free tests. Explicit checks work without the JVM -ea option. */
public final class StructuresTest {
    private static int passed;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void test(String name, Runnable body) {
        body.run();
        passed++;
        System.out.println("PASS: " + name);
    }

    private static void basic(Supplier<IntSet> factory) {
        IntSet set = factory.get();
        check(!set.search(1) && !set.delete(1) && set.size() == 0, "empty set");
        int[] keys = {0, -7, 16, 32, Integer.MIN_VALUE, Integer.MAX_VALUE};
        for (int key : keys) {
            check(set.insert(key), "new key");
            check(!set.insert(key), "duplicate");
            check(set.search(key), "membership");
        }
        check(set.size() == keys.length, "size after insertion");
        for (int key : keys) {
            check(set.delete(key), "existing deletion");
            check(!set.delete(key) && !set.search(key), "missing key");
        }
        check(set.size() == 0, "size after deletion");
        check(set.insert(16) && set.search(16), "reinsertion");
    }

    private static void mixed(Supplier<IntSet> factory) {
        IntSet actual = factory.get();
        Set<Integer> expected = new HashSet<>(); // Test oracle only.
        Random random = new Random(314159);
        for (int i = 0; i < 12000; i++) {
            int key = random.nextInt(701) - 350;
            switch (random.nextInt(3)) {
                case 0 -> check(actual.insert(key) == expected.add(key), "mixed insertion");
                case 1 -> check(actual.search(key) == expected.contains(key), "mixed search");
                case 2 -> check(actual.delete(key) == expected.remove(key), "mixed deletion");
                default -> throw new AssertionError();
            }
            check(actual.size() == expected.size(), "mixed size");
            if (i % 128 == 0)
                for (int candidate = -350; candidate <= 350; candidate++)
                    check(actual.search(candidate) == expected.contains(candidate), "surviving keys");
        }
        for (int key = -350; key <= 350; key++)
            check(actual.search(key) == expected.contains(key), "final membership");
    }

    public static void main(String[] args) {
        test("BST empty, duplicates, integer boundaries, deletion, reinsertion", () -> basic(BST::new));
        test("HashTable basic behavior", () -> basic(HashTable::new));
        test("HashTable basic behavior with collisions", () -> basic(() -> new HashTable(16, key -> 0)));
        test("BST 12,000 mixed operations match HashSet", () -> mixed(BST::new));
        test("HashTable 12,000 mixed operations match HashSet", () -> mixed(HashTable::new));
        test("Colliding HashTable 12,000 mixed operations match HashSet",
                () -> mixed(() -> new HashTable(16, key -> 0)));

        test("BST leaf, root, one-child, two-child and successor-right-child deletion", () -> {
            BST tree = new BST();
            Set<Integer> remaining = new HashSet<>();
            int[] keys = {50, 20, 80, 10, 30, 60, 90, 65};
            for (int key : keys) { tree.insert(key); remaining.add(key); }
            for (int key : new int[]{50, 10, 60, 20, 30, 80, 90, 65}) {
                check(tree.delete(key), "delete chosen node");
                remaining.remove(key);
                for (int candidate : keys)
                    check(tree.search(candidate) == remaining.contains(candidate), "preserve other keys");
            }
            check(tree.size() == 0 && tree.height() == 0, "empty BST");
            tree.insert(2); tree.insert(1); tree.insert(3);
            check(tree.delete(2) && tree.search(1) && tree.search(3), "immediate successor");
        });

        test("BST monotonic input uses no recursive call stack", () -> {
            BST tree = new BST();
            for (int i = 0; i < 2000; i++) tree.insert(i);
            check(tree.height() == 2000 && tree.search(1999), "linear height");
            for (int i = 1999; i >= 0; i--) check(tree.delete(i), "reverse deletion");
            check(tree.height() == 0, "empty after deletion");
        });

        test("HashTable resizing and collision-chain deletion preserve keys", () -> {
            HashTable table = new HashTable(1, key -> 0);
            for (int i = 0; i < 500; i++) table.insert(i);
            check(table.loadFactor() <= 0.75 && table.maxChainLength() == 500, "capacity and chain");
            for (int i = 0; i < 500; i++) check(table.search(i), "preserved after rehash");
            for (int i = 0; i < 500; i += 2) check(table.delete(i), "remove alternating keys");
            for (int i = 0; i < 500; i++) check(table.search(i) == (i % 2 == 1), "remaining chain");
        });

        test("HashTable rejects invalid constructor arguments", () -> {
            try { new HashTable(0, key -> key); throw new AssertionError("accepted zero capacity"); }
            catch (IllegalArgumentException expected) { /* expected */ }
            try { new HashTable(16, null); throw new AssertionError("accepted null hash"); }
            catch (NullPointerException expected) { /* expected */ }
        });
        System.out.println("All " + passed + " tests passed.");
    }
}
