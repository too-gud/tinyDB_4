package Engine;

import storage.Row;
import storage.Table;

import java.util.List;

public class DeleteExecutor {

    public void execute(DatabaseEngine db,
                        String tableName,
                        List<Condition> conditions,
                        List<String> logicalOperators) {

        Table table = db.getTable(tableName);

        List<Row> matches = ConditionEvaluator.findMatches(table, conditions, logicalOperators);

        for (Row row : matches) {
            table.removeRow(row);
        }

        System.out.println(matches.size() + " row(s) deleted.");
    }
}
