package Engine;


import storage.Table;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DatabaseEngine {

    private final Map<String, Table> tables;

    public DatabaseEngine() {
        this.tables = new HashMap<>();
    }

    // CREATE TABLE
    public void createTable(Table table) {
        String tableName = table.getTableName();

        if (tables.containsKey(tableName)) {
            throw new IllegalArgumentException(
                    "Table '" + tableName + "' already exists."
            );
        }

        tables.put(tableName, table);
    }

    // DROP TABLE
    public void dropTable(String tableName) {
        if (!tables.containsKey(tableName)) {
            throw new IllegalArgumentException(
                    "Table '" + tableName + "' does not exist."
            );
        }

        tables.remove(tableName);
    }

    // GET TABLE
    public Table getTable(String tableName) {
        Table table = tables.get(tableName);

        if (table == null) {
            throw new IllegalArgumentException(
                    "Table '" + tableName + "' does not exist."
            );
        }

        return table;
    }

    // CHECK IF TABLE EXISTS
    public boolean tableExists(String tableName) {
        return tables.containsKey(tableName);
    }

    // LIST ALL TABLES
    public Collection<Table> getAllTables() {
        return tables.values();
    }
}