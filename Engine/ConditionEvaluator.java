package Engine;

import storage.Row;
import storage.Table;

import java.util.List;

/**
 * Evaluates a WHERE clause - a list of Conditions chained by AND/OR -
 * against a table's rows. Shared by SELECT, UPDATE, and DELETE so each
 * doesn't need its own copy of condition-matching logic.
 *
 * Conditions are combined strictly left to right with no operator
 * precedence (no parentheses support): "a OR b AND c" is evaluated as
 * "(a OR b) AND c". This mirrors how a lot of simple query engines start
 * out, and keeps the logic easy to trace by hand.
 */
public class ConditionEvaluator {

    /**
     * Returns every row in the table that satisfies the WHERE clause.
     *
     * When every condition is AND'd together and one of them filters on
     * the primary key, the B+ Tree is used to narrow candidates down
     * first - the remaining conditions are only checked against that
     * smaller set instead of scanning the whole table. If any OR is
     * involved, a single indexed condition can no longer safely narrow the
     * result set (an OR branch could make a row match independently of
     * it), so every row has to be checked.
     */
    public static List<Row> findMatches(Table table, List<Condition> conditions, List<String> logicalOperators) {

        List<Row> candidates = chooseCandidateRows(table, conditions, logicalOperators);

        List<Row> matches = new java.util.ArrayList<>();

        for (Row row : candidates) {
            if (matches(row, table, conditions, logicalOperators)) {
                matches.add(row);
            }
        }

        return matches;
    }

    // Picks the smallest set of rows worth checking one by one. Falls back
    // to the full row list whenever the index can't safely be used.
    private static List<Row> chooseCandidateRows(Table table, List<Condition> conditions, List<String> logicalOperators) {

        boolean allAnd = true;

        for (String operator : logicalOperators) {
            if (!operator.equalsIgnoreCase("AND")) {
                allAnd = false;
                break;
            }
        }

        if (allAnd) {
            for (Condition condition : conditions) {
                if (condition.getColumnName().equalsIgnoreCase(table.getPrimaryKeyColumnName())) {
                    return table.queryByPrimaryKey(condition.getOperator(), condition.getValue());
                }
            }
        }

        return table.getRows();
    }

    /** Evaluates the full WHERE clause against a single row. */
    public static boolean matches(Row row, Table table, List<Condition> conditions, List<String> logicalOperators) {

        boolean result = evaluateSingleCondition(row, table, conditions.get(0));

        for (int i = 1; i < conditions.size(); i++) {

            boolean next = evaluateSingleCondition(row, table, conditions.get(i));
            String operator = logicalOperators.get(i - 1);

            if (operator.equalsIgnoreCase("AND")) {
                result = result && next;
            }
            else if (operator.equalsIgnoreCase("OR")) {
                result = result || next;
            }
            else {
                throw new RuntimeException("Unsupported logical operator: " + operator);
            }
        }

        return result;
    }

    private static boolean evaluateSingleCondition(Row row, Table table, Condition condition) {

        int columnIndex = findColumnIndex(table, condition.getColumnName());
        Object currentValue = row.getValues().get(columnIndex);

        return evaluateComparison(currentValue, condition.getOperator(), condition.getValue());
    }

    public static int findColumnIndex(Table table, String columnName) {

        for (int i = 0; i < table.getColumns().size(); i++) {
            if (table.getColumns().get(i).getName().equalsIgnoreCase(columnName)) {
                return i;
            }
        }

        throw new RuntimeException("Column '" + columnName + "' not found.");
    }

    @SuppressWarnings("unchecked")
    public static boolean evaluateComparison(Object currentValue, String operator, Object value) {

        switch (operator) {

            case "=":
                return currentValue.equals(value);

            case "<>":
                return !currentValue.equals(value);

            case "<":
                return ((Comparable) currentValue).compareTo(value) < 0;

            case "<=":
                return ((Comparable) currentValue).compareTo(value) <= 0;

            case ">":
                return ((Comparable) currentValue).compareTo(value) > 0;

            case ">=":
                return ((Comparable) currentValue).compareTo(value) >= 0;

            default:
                throw new RuntimeException("Unsupported operator: " + operator);
        }
    }
}
