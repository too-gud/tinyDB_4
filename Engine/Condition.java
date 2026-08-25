package Engine;

/**
 * A single WHERE comparison, e.g. "age > 30" becomes
 * columnName="age", operator=">", value=30.
 *
 * A full WHERE clause is a list of these chained together by AND/OR -
 * see ConditionEvaluator for how they're combined and evaluated.
 */
public class Condition {

    private final String columnName;
    private final String operator;
    private final Object value;

    public Condition(String columnName, String operator, Object value) {
        this.columnName = columnName;
        this.operator = operator;
        this.value = value;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getOperator() {
        return operator;
    }

    public Object getValue() {
        return value;
    }
}
