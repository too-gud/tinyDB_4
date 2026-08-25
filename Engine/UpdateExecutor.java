package Engine;

import storage.Row;
import storage.Table;

import java.util.List;

public class UpdateExecutor {

    public void execute(DatabaseEngine db,
                        String tableName,
                        String updateColumn,
                        Object newValue,
                        List<Condition> conditions,
                        List<String> logicalOperators) {

        Table table = db.getTable(tableName);

        int updateIndex = ConditionEvaluator.findColumnIndex(table, updateColumn);

        List<Row> matches = ConditionEvaluator.findMatches(table, conditions, logicalOperators);

        for (Row row : matches) {
            applyUpdate(table, row, updateIndex, newValue);
        }

        System.out.println(matches.size() + " row(s) updated.");
    }

    // Applies the new value to a row. If the column being updated is the
    // primary key itself, the B+ Tree index has to be updated too - the
    // row's old key needs to be removed and the new key inserted, or the
    // index would still point to the value under its stale key.
    private void applyUpdate(Table table, Row row, int updateIndex, Object newValue) {

        if (updateIndex == table.getPrimaryKeyColumnIndex()) {
            Object oldKey = row.getValues().get(updateIndex);
            row.getValues().set(updateIndex, newValue);
            table.reindexRow(row, oldKey);
        }
        else {
            row.getValues().set(updateIndex, newValue);
        }
    }
}
