package index;

import java.util.ArrayList;
import java.util.List;

/**
 * A generic in-memory B+ Tree, used by TinyDB to index a table's primary key.
 *
 * How it's structured:
 *  - Internal nodes only ever hold keys used to route a search to the right
 *    child; they never store actual values.
 *  - Leaf nodes hold the actual (key, value) pairs.
 *  - All leaves are linked together left-to-right (a doubly linked list),
 *    which is the defining feature of a B+ Tree over a plain B-Tree: it
 *    makes ordered range scans fast, since you don't have to walk back up
 *    and down the tree to move from one key to the next.
 *
 * Key type note: K is bound to the raw Comparable type (rather than the
 * usual self-referencing Comparable<K>) because TinyDB stores column values
 * as plain Object at the storage layer - a table's primary key column might
 * hold Integer or String values depending on what was declared. Using a raw
 * Comparable bound lets one BplusTree instance index either, as long as all
 * keys inserted into it are mutually comparable (true in practice, since a
 * single column always holds one consistent Java type).
 *
 * @param <K> the key type (must implement Comparable)
 * @param <V> the value type stored at the leaves (e.g. a Row)
 */
public class BplusTree<K extends Comparable, V> {

    // Maximum keys a node may hold before it must split. With ORDER = 4,
    // every node holds at most 3 keys and has at most 4 children.
    private static final int ORDER = 4;

    private Node root;
    private int size;

    public BplusTree() {
        this.root = new LeafNode();
    }

    // ---------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------

    /** Returns the value stored under key, or null if the key isn't present. */
    @SuppressWarnings("unchecked")
    public V search(K key) {
        LeafNode leaf = findLeaf(key);
        int index = leaf.keys.indexOf(key);
        return (index == -1) ? null : leaf.values.get(index);
    }

    public boolean contains(K key) {
        return search(key) != null;
    }

    /**
     * Returns every value whose key falls between startKey and endKey
     * (inclusive on both ends), in ascending key order. This is the
     * classic B+ Tree range scan: find the starting leaf, then just walk
     * the leaf chain forward instead of re-searching from the root.
     */
    public List<V> rangeSearch(K startKey, K endKey) {
        return rangeSearch(startKey, true, endKey, true);
    }

    /**
     * General-purpose range scan backing SQL comparisons like
     * {@code column < 10} or {@code column >= 5}: either bound can be null
     * to mean "unbounded" (scan to the start/end of the tree), and each
     * bound can independently be inclusive or exclusive. This is what
     * powers SELECT ... WHERE when the WHERE column is the primary key.
     */
    @SuppressWarnings("unchecked")
    public List<V> rangeSearch(K startKey, boolean startInclusive, K endKey, boolean endInclusive) {

        List<V> results = new ArrayList<>();
        LeafNode leaf = (startKey == null) ? leftmostLeaf() : findLeaf(startKey);

        while (leaf != null) {

            for (int i = 0; i < leaf.keys.size(); i++) {

                K key = leaf.keys.get(i);

                if (startKey != null) {
                    int cmpStart = key.compareTo(startKey);
                    if (cmpStart < 0 || (cmpStart == 0 && !startInclusive)) {
                        continue; // before the range starts
                    }
                }

                if (endKey != null) {
                    int cmpEnd = key.compareTo(endKey);
                    if (cmpEnd > 0 || (cmpEnd == 0 && !endInclusive)) {
                        return results; // gone past the range, no need to look further
                    }
                }

                results.add(leaf.values.get(i));
            }

            leaf = leaf.next;
        }

        return results;
    }

    // Walks down from the root to the leaf that should contain `key`.
    @SuppressWarnings("unchecked")
    private LeafNode findLeaf(K key) {

        Node node = root;

        while (node instanceof BplusTree.InternalNode) {
            InternalNode internal = (InternalNode) node;
            node = internal.children.get(internal.findChildIndex(key));
        }

        return (LeafNode) node;
    }

    // ---------------------------------------------------------------
    // Insert
    // ---------------------------------------------------------------

    public void insert(K key, V value) {

        LeafNode leaf = findLeaf(key);

        if (leaf.keys.contains(key)) {
            throw new IllegalArgumentException("Duplicate key: " + key);
        }

        leaf.insertSorted(key, value);
        size++;

        if (leaf.keys.size() > ORDER - 1) {
            splitLeaf(leaf);
        }
    }

    // Splits an over-full leaf in half, links the new leaf into the leaf
    // chain, and copies the new leaf's first key up into the parent.
    private void splitLeaf(LeafNode leaf) {

        int mid = leaf.keys.size() / 2;

        LeafNode newLeaf = new LeafNode();
        newLeaf.keys.addAll(leaf.keys.subList(mid, leaf.keys.size()));
        newLeaf.values.addAll(leaf.values.subList(mid, leaf.values.size()));

        // Bulk-remove the moved entries from the original leaf
        leaf.keys.subList(mid, leaf.keys.size()).clear();
        leaf.values.subList(mid, leaf.values.size()).clear();

        // Splice the new leaf into the leaf-level doubly linked list
        newLeaf.next = leaf.next;
        newLeaf.previous = leaf;
        if (leaf.next != null) {
            leaf.next.previous = newLeaf;
        }
        leaf.next = newLeaf;

        // A leaf's smallest key is copied (not moved) up to the parent as
        // the separator between the old leaf and the new one.
        K risingKey = newLeaf.keys.get(0);

        insertIntoParent(leaf, risingKey, newLeaf);
    }

    // Splits an over-full internal node. Unlike a leaf split, the middle
    // key is pushed up (not copied) since internal nodes only route search,
    // they don't need to store their own copy of every key.
    private void splitInternal(InternalNode node) {

        int mid = node.keys.size() / 2;
        K risingKey = node.keys.get(mid);

        InternalNode newInternal = new InternalNode();
        newInternal.keys.addAll(node.keys.subList(mid + 1, node.keys.size()));
        newInternal.children.addAll(node.children.subList(mid + 1, node.children.size()));

        for (Node child : newInternal.children) {
            child.parent = newInternal;
        }

        node.keys.subList(mid, node.keys.size()).clear();
        node.children.subList(mid + 1, node.children.size()).clear();

        insertIntoParent(node, risingKey, newInternal);
    }

    // Inserts risingKey (and its right-hand child rightNode) into the
    // parent of leftNode. If leftNode had no parent (it was the root),
    // a brand new root is created one level up.
    private void insertIntoParent(Node leftNode, K risingKey, Node rightNode) {

        InternalNode parent = leftNode.parent;

        if (parent == null) {
            InternalNode newRoot = new InternalNode();
            newRoot.keys.add(risingKey);
            newRoot.children.add(leftNode);
            newRoot.children.add(rightNode);

            leftNode.parent = newRoot;
            rightNode.parent = newRoot;

            root = newRoot;
            return;
        }

        int leftIndex = parent.children.indexOf(leftNode);
        parent.keys.add(leftIndex, risingKey);
        parent.children.add(leftIndex + 1, rightNode);
        rightNode.parent = parent;

        if (parent.keys.size() > ORDER - 1) {
            splitInternal(parent);
        }
    }

    // ---------------------------------------------------------------
    // Delete
    // ---------------------------------------------------------------

    /** Removes a key (and its value) from the tree. Returns false if absent. */
    public boolean delete(K key) {

        LeafNode leaf = findLeaf(key);
        int index = leaf.keys.indexOf(key);

        if (index == -1) {
            return false;
        }

        leaf.keys.remove(index);
        leaf.values.remove(index);
        size--;

        // If the leaf is now completely empty (and isn't the root), it no
        // longer earns its place in the tree - detach it entirely.
        if (leaf != root && leaf.keys.isEmpty()) {
            removeEmptyLeaf(leaf);
        }

        return true;
    }

    // Detaches a now-empty leaf from its parent, fixes up the leaf chain,
    // and collapses the parent if it's been left with just one child.
    //
    // Note: this keeps the tree correct (search/insert continue to work)
    // but is a simplified rebalancing strategy - it only merges/collapses
    // nodes that become completely empty or single-child, rather than
    // enforcing a strict minimum occupancy with borrow-from-sibling logic
    // like a textbook B+ Tree. For TinyDB's scale this keeps the deletion
    // logic easy to follow while never producing an invalid tree.
    private void removeEmptyLeaf(LeafNode leaf) {

        InternalNode parent = leaf.parent;
        int leafIndex = parent.children.indexOf(leaf);

        parent.children.remove(leafIndex);

        // Every child except the leftmost has exactly one separator key
        // immediately to its left in the parent's key list.
        int keyIndex = Math.max(leafIndex - 1, 0);
        if (!parent.keys.isEmpty()) {
            parent.keys.remove(keyIndex);
        }

        // Unlink the leaf from the leaf-level chain
        if (leaf.previous != null) {
            leaf.previous.next = leaf.next;
        }
        if (leaf.next != null) {
            leaf.next.previous = leaf.previous;
        }

        collapseIfNeeded(parent);
    }

    // If an internal node has been reduced to a single child, it's just
    // dead weight - replace it in its own parent with that one child
    // directly (or, if it was the root, make the child the new root).
    private void collapseIfNeeded(InternalNode node) {

        if (node.children.size() > 1) {
            return;
        }

        Node onlyChild = node.children.get(0);

        if (node == root) {
            onlyChild.parent = null;
            root = onlyChild;
            return;
        }

        InternalNode parent = node.parent;
        int nodeIndex = parent.children.indexOf(node);

        parent.children.set(nodeIndex, onlyChild);
        onlyChild.parent = parent;
    }

    // ---------------------------------------------------------------
    // Misc
    // ---------------------------------------------------------------

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns every key currently in the tree, in ascending order, by
     * walking the leaf chain from left to right. Mainly useful for
     * debugging/testing that the tree stays correctly sorted after a
     * sequence of inserts and deletes.
     */
    public List<K> allKeysInOrder() {

        List<K> allKeys = new ArrayList<>();
        LeafNode leaf = leftmostLeaf();

        while (leaf != null) {
            allKeys.addAll(leaf.keys);
            leaf = leaf.next;
        }

        return allKeys;
    }

    @SuppressWarnings("unchecked")
    private LeafNode leftmostLeaf() {

        Node node = root;

        while (node instanceof BplusTree.InternalNode) {
            node = ((InternalNode) node).children.get(0);
        }

        return (LeafNode) node;
    }

    // ---------------------------------------------------------------
    // Node types
    // ---------------------------------------------------------------
    // These are non-static inner classes on purpose: they need to share
    // the enclosing BplusTree's K and V type parameters, and they're never
    // used outside this class.

    private abstract class Node {
        List<K> keys = new ArrayList<>();
        InternalNode parent;
    }

    private class LeafNode extends Node {

        List<V> values = new ArrayList<>();
        LeafNode next;
        LeafNode previous;

        // Inserts a (key, value) pair, keeping keys/values sorted by key.
        @SuppressWarnings("unchecked")
        void insertSorted(K key, V value) {

            int i = 0;
            while (i < keys.size() && keys.get(i).compareTo(key) < 0) {
                i++;
            }

            keys.add(i, key);
            values.add(i, value);
        }
    }

    private class InternalNode extends Node {

        List<Node> children = new ArrayList<>();

        // Finds which child to descend into for a given search key: the
        // first child whose "gap" the key falls into.
        @SuppressWarnings("unchecked")
        int findChildIndex(K key) {

            int i = 0;
            while (i < keys.size() && key.compareTo(keys.get(i)) >= 0) {
                i++;
            }

            return i;
        }
    }
}
