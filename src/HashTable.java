import java.util.Objects;
import java.util.function.IntUnaryOperator;

/** Separate chaining, with geometric growth above load factor 0.75. */
public final class HashTable implements IntSet {
    private static final class Node {
        final int key;
        Node next;
        Node(int key, Node next) { this.key = key; this.next = next; }
    }

    private Node[] buckets;
    private final IntUnaryOperator hash;
    private int size;

    public HashTable() { this(16, key -> key); }

    /** Custom deterministic hashes make collision behavior testable. */
    public HashTable(int initialCapacity, IntUnaryOperator hash) {
        if (initialCapacity < 1)
            throw new IllegalArgumentException("Capacity must be positive");
        this.hash = Objects.requireNonNull(hash, "hash");
        buckets = new Node[initialCapacity];
    }

    private int index(int key, int capacity) {
        // floorMod also handles negative keys and Integer.MIN_VALUE.
        return Math.floorMod(hash.applyAsInt(key), capacity);
    }

    @Override
    public boolean insert(int key) {
        int index = index(key, buckets.length);
        for (Node node = buckets[index]; node != null; node = node.next)
            if (node.key == key) return false;
        if (((long) size + 1) * 4 > (long) buckets.length * 3) {
            if (buckets.length > Integer.MAX_VALUE / 2)
                throw new IllegalStateException("Maximum table capacity reached");
            resize(buckets.length * 2);
            index = index(key, buckets.length);
        }
        buckets[index] = new Node(key, buckets[index]);
        size++;
        return true;
    }

    @Override
    public boolean search(int key) {
        for (Node node = buckets[index(key, buckets.length)]; node != null; node = node.next)
            if (node.key == key) return true;
        return false;
    }

    @Override
    public boolean delete(int key) {
        int index = index(key, buckets.length);
        Node previous = null;
        Node node = buckets[index];
        while (node != null) {
            if (node.key == key) {
                if (previous == null) buckets[index] = node.next;
                else previous.next = node.next;
                size--;
                return true;
            }
            previous = node;
            node = node.next;
        }
        return false;
    }

    private void resize(int capacity) {
        Node[] replacement = new Node[capacity];
        for (Node head : buckets) {
            Node node = head;
            while (node != null) {
                Node next = node.next;
                int index = index(node.key, capacity);
                node.next = replacement[index];
                replacement[index] = node;
                node = next;
            }
        }
        buckets = replacement;
    }

    @Override
    public int size() { return size; }
    public int capacity() { return buckets.length; }
    public double loadFactor() { return (double) size / buckets.length; }

    /** Diagnostic only; not part of timed core operations. */
    public int maxChainLength() {
        int maximum = 0;
        for (Node head : buckets) {
            int length = 0;
            for (Node node = head; node != null; node = node.next) length++;
            maximum = Math.max(maximum, length);
        }
        return maximum;
    }
}
