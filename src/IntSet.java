/** A set of unique 32-bit integer keys. */
public interface IntSet {
    /** @return true if this call adds a new key */
    boolean insert(int key);
    boolean search(int key);
    /** @return true if this call removes an existing key */
    boolean delete(int key);
    int size();
}
