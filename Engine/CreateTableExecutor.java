package Engine;


import storage.Column;
import storage.Table;

import java.util.List;

public class CreateTableExecutor {

    public void execute(DatabaseEngine db,
                        String tableName,
                        List<Column> columns) {

        // Check if table already exists
        if (db.tableExists(tableName)) {
            throw new RuntimeException(
                    "Table '" + tableName + "' already exists."
            );
        }

        // Create the table
        Table table = new Table(tableName, columns);

        // Register it in the database
        db.createTable(table);

        System.out.println("Table '" + tableName + "' created successfully.");
    }
}