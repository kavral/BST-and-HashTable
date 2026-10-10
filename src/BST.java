import java.util.ArrayDeque;
import java.util.Deque;

/** An unbalanced binary search tree. Core operations are iterative. */
public final class BST implements IntSet {
    private static final class Node {
        int key;
        Node left;
        Node right;
        Node(int key) { this.key = key; }
    }

    private Node root;
    private int size;

    @Override
    public boolean insert(int key) {
        Node parent = null;
        Node node = root;
        while (node != null) {
            if (key == node.key) return false;
            parent = node;
            node = key < node.key ? node.left : node.right;
        }
        Node leaf = new Node(key);
        if (parent == null) root = leaf;
        else if (key < parent.key) parent.left = leaf;
        else parent.right = leaf;
        size++;
        return true;
    }

    @Override
    public boolean search(int key) {
        Node node = root;
        while (node != null) {
            if (key == node.key) return true;
            node = key < node.key ? node.left : node.right;
        }
        return false;
    }

    @Override
    public boolean delete(int key) {
        Node parent = null;
        Node node = root;
        while (node != null && node.key != key) {
            parent = node;
            node = key < node.key ? node.left : node.right;
        }
        if (node == null) return false;
        // For two children, copy the successor key and unlink the successor.
        if (node.left != null && node.right != null) {
            Node successorParent = node;
            Node successor = node.right;
            while (successor.left != null) {
                successorParent = successor;
                successor = successor.left;
            }
            node.key = successor.key;
            parent = successorParent;
            node = successor;
        }
        Node child = node.left != null ? node.left : node.right;
        if (parent == null) root = child;
        else if (parent.left == node) parent.left = child;
        else parent.right = child;
        size--;
        return true;
    }

    @Override
    public int size() { return size; }

    /** Diagnostic only: height in nodes, with empty-tree height zero. */
    public int height() {
        if (root == null) return 0;
        record Frame(Node node, int depth) {}
        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(root, 1));
        int maximum = 0;
        while (!stack.isEmpty()) {
            Frame frame = stack.pop();
            maximum = Math.max(maximum, frame.depth());
            if (frame.node().left != null)
                stack.push(new Frame(frame.node().left, frame.depth() + 1));
            if (frame.node().right != null)
                stack.push(new Frame(frame.node().right, frame.depth() + 1));
        }
        return maximum;
    }
}
