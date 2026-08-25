package Engine;

import storage.Row;
import storage.Table;

import java.util.ArrayList;
import java.util.List;

public class SelectExecutor {

    // selectedColumns == null means "SELECT *" (project every column).
    public void select(DatabaseEngine db, String tableName, List<String> selectedColumns) {

        Table table = db.getTable(tableName);

        printRows(table, table.getRows(), selectedColumns);
    }

    public void selectWhere(DatabaseEngine db,
                            String tableName,
                            List<String> selectedColumns,
                            List<Condition> conditions,
                            List<String> logicalOperators) {

        Table table = db.getTable(tableName);

        List<Row> matches = ConditionEvaluator.findMatches(table, conditions, logicalOperators);

        printRows(table, matches, selectedColumns);
    }

    // Turns the requested column names into their positions in the table's
    // column list, so both the header and each row can be printed using
    // just those positions. null (from "SELECT *") means every column.
    private List<Integer> resolveColumnIndices(Table table, List<String> selectedColumns) {

        List<Integer> indices = new ArrayList<>();

        if (selectedColumns == null) {
            for (int i = 0; i < table.getColumns().size(); i++) {
                indices.add(i);
            }
            return indices;
        }

        for (String columnName : selectedColumns) {
            indices.add(ConditionEvaluator.findColumnIndex(table, columnName));
        }

        return indices;
    }

    private void printRows(Table table, List<Row> rows, List<String> selectedColumns) {

        List<Integer> columnIndices = resolveColumnIndices(table, selectedColumns);

        // Print column names (only the projected ones)
        for (int index : columnIndices) {
            System.out.print(table.getColumns().get(index).getName() + "\t");
        }
        System.out.println();

        // Print separator
        for (int i = 0; i < columnIndices.size(); i++) {
            System.out.print("--------");
        }
        System.out.println();

        // Print matching rows, projecting only the requested columns
        for (Row row : rows) {
            for (int index : columnIndices) {
                System.out.print(row.getValues().get(index) + "\t");
            }
            System.out.println();
        }

        System.out.println(rows.size() + " row(s) returned.");
    }
}
