package storage;

import index.BplusTree;

import java.util.*;

public class Table {

    private String tableName;
    private List<Column> columns;
    private List<Row> rows;

    // By convention, the first declared column is the table's primary key.
    // This index maps primary-key value -> Row, giving O(log n) lookups
    // and duplicate-key rejection instead of scanning every row.
    private static final int PRIMARY_KEY_COLUMN_INDEX = 0;
    private final BplusTree<Comparable, Row> primaryIndex;

    public Table(String tableName, List<Column> columns) {
        this.tableName = tableName;
        this.columns = columns;
        this.rows = new ArrayList<>();
        this.primaryIndex = new BplusTree<>();
    }

    // Inserts a row, enforcing primary-key uniqueness and keeping the
    // B+ Tree index in sync with the row list.
    public void insert(Row row) {

        Comparable key = primaryKeyOf(row);

        if (primaryIndex.contains(key)) {
            throw new RuntimeException(
                    "Duplicate value '" + key + "' for primary key column '" +
                    getPrimaryKeyColumnName() + "'."
            );
        }

        rows.add(row);
        primaryIndex.insert(key, row);
    }

    // O(log n) lookup by primary key, using the B+ Tree instead of a scan.
    // Returns null if no row has that key.
    public Row findByPrimaryKey(Object key) {
        return primaryIndex.search((Comparable) key);
    }

    // Removes a row from both the row list and the index. Used by
    // DELETE (and by UPDATE when it needs to move a row to a new key).
    public void removeRow(Row row) {
        rows.remove(row);
        primaryIndex.delete(primaryKeyOf(row));
    }

    // Called after a row's primary-key column value has just been changed
    // in place (e.g. by an UPDATE), so the index points at the new key
    // instead of the stale one.
    public void reindexRow(Row row, Object oldKey) {
        primaryIndex.delete((Comparable) oldKey);
        primaryIndex.insert(primaryKeyOf(row), row);
    }

    // Answers a SELECT ... WHERE <primaryKeyColumn> <operator> <value> by
    // routing to the appropriate B+ Tree operation instead of scanning
    // every row. "=" is a direct O(log n) lookup; "<", "<=", ">", ">="
    // become one-sided range scans over the leaf chain. "<>" can't be
    // answered efficiently by a tree keyed on equality/order in a useful
    // way here, so it falls back to a scan.
    public List<Row> queryByPrimaryKey(String operator, Object value) {

        Comparable key = (Comparable) value;
        List<Row> results = new ArrayList<>();

        switch (operator) {

            case "=":
                Row match = primaryIndex.search(key);
                if (match != null) {
                    results.add(match);
                }
                return results;

            case "<":
                return primaryIndex.rangeSearch(null, true, key, false);

            case "<=":
                return primaryIndex.rangeSearch(null, true, key, true);

            case ">":
                return primaryIndex.rangeSearch(key, false, null, true);

            case ">=":
                return primaryIndex.rangeSearch(key, true, null, true);

            case "<>":
                for (Row row : rows) {
                    if (!primaryKeyOf(row).equals(key)) {
                        results.add(row);
                    }
                }
                return results;

            default:
                throw new RuntimeException("Unsupported operator: " + operator);
        }
    }

    private Comparable primaryKeyOf(Row row) {
        return (Comparable) row.getValues().get(PRIMARY_KEY_COLUMN_INDEX);
    }

    public int getPrimaryKeyColumnIndex() {
        return PRIMARY_KEY_COLUMN_INDEX;
    }

    public String getPrimaryKeyColumnName() {
        return columns.get(PRIMARY_KEY_COLUMN_INDEX).getName();
    }

    public List<Row> getRows() {
        return rows;
    }

    public List<Column> getColumns() {
        return columns;
    }

    public String getTableName() {
        return tableName;
    }
}
