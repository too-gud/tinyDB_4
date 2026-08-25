package Engine;

import storage.Row;
import storage.Table;

import java.util.List;

public class InsertExecutor {

    public void execute(DatabaseEngine db, String tableName, List<Object> values) {
        Table table = db.getTable(tableName);
        // Check if the number of values matches the number of columns
        if (values.size() != table.getColumns().size()) {
            throw new IllegalArgumentException(
                    "Expected " + table.getColumns().size() +
                    " values but got " + values.size()
            );
        }

        // Create a new row
        Row row = new Row(values);

        // Add it to the table
        table.insert(row);

        System.out.println("Row inserted successfully.");
    }
}