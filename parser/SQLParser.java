package parser;

import Engine.DatabaseEngine;
import Engine.DeleteExecutor;
import Engine.InsertExecutor;
import Engine.SelectExecutor;
import Engine.CreateTableExecutor;
import Engine.UpdateExecutor;
import Engine.Condition;

import storage.Column;

import java.util.ArrayList;
import java.util.List;

public class SQLParser {

    private final Tokenizer tokenizer;

    public SQLParser() {
        tokenizer = new Tokenizer();
    }

    public void execute(String query, DatabaseEngine db) {

        List<String> tokens = tokenizer.tokenize(query);

        if (tokens.isEmpty()) {
            return;
        }

        String command = tokens.get(0).toUpperCase();

        switch (command) {

            case "CREATE":
                parseCreate(tokens, db);
                break;

            case "INSERT":
                parseInsert(tokens, db);
                break;

            case "SELECT":
                parseSelect(tokens, db);
                break;

            case "DELETE":
                parseDelete(tokens, db);
                break;

            case "UPDATE":
                parseUpdate(tokens, db);
                break;

            case "DROP":
                parseDropTable(tokens, db);
                break;

            default:
                throw new RuntimeException("Unsupported query.");
        }
    }

    private void parseCreate(List<String> tokens, DatabaseEngine db) {

        String tableName = tokens.get(2);

        List<Column> columns = new ArrayList<>();

        for (int i = 4; i < tokens.size(); i++) {

            if (tokens.get(i).equals(")")) {
                break;
            }

            if (tokens.get(i).equals(",")) {
                continue;
            }

            String columnName = tokens.get(i);
            String columnType = tokens.get(i + 1);

            columns.add(new Column(columnName, columnType));

            i++;
        }

        CreateTableExecutor create = new CreateTableExecutor();
        create.execute(db, tableName, columns);
    }

    private void parseInsert(List<String> tokens, DatabaseEngine db) {

        String tableName = tokens.get(2);

        List<Object> values = new ArrayList<>();

        boolean readingValues = false;

        for (String token : tokens) {

            if (token.equals("(")) {
                readingValues = true;
                continue;
            }

            if (token.equals(")")) {
                break;
            }

            if (readingValues && !token.equals(",")) {
                values.add(parseValue(token));
            }
        }

        InsertExecutor insert = new InsertExecutor();
        insert.execute(db, tableName, values);
    }

    private void parseSelect(List<String> tokens, DatabaseEngine db) {

        // SELECT (* | col1, col2, ...) FROM tableName [WHERE cond1 (AND|OR) cond2 ...]
        int fromIndex = indexOfKeyword(tokens, "FROM");

        if (fromIndex == -1) {
            throw new RuntimeException("SELECT requires a FROM clause.");
        }

        List<String> selectedColumns = parseSelectColumns(tokens, fromIndex);

        String tableName = tokens.get(fromIndex + 1);

        SelectExecutor select = new SelectExecutor();

        int whereIndex = indexOfKeyword(tokens, "WHERE");

        if (whereIndex == -1) {
            select.select(db, tableName, selectedColumns);
            return;
        }

        List<String> logicalOperators = new ArrayList<>();
        List<Condition> conditions = parseConditions(tokens, whereIndex, logicalOperators);

        select.selectWhere(db, tableName, selectedColumns, conditions, logicalOperators);
    }

    // Parses the column list between SELECT and FROM. "*" means "every
    // column", represented as null so the executor knows to project all
    // of them rather than looking up a column literally named "*".
    // Anything else is read as a comma-separated list of column names,
    // e.g. "id, name".
    private List<String> parseSelectColumns(List<String> tokens, int fromIndex) {

        List<String> selectedColumns = new ArrayList<>();

        for (int i = 1; i < fromIndex; i++) {

            String token = tokens.get(i);

            if (token.equals(",")) {
                continue;
            }

            selectedColumns.add(token);
        }

        if (selectedColumns.size() == 1 && selectedColumns.get(0).equals("*")) {
            return null;
        }

        return selectedColumns;
    }

    private void parseDelete(List<String> tokens, DatabaseEngine db) {

        // DELETE FROM tableName WHERE cond1 (AND|OR) cond2 ...
        String tableName = tokens.get(2);

        int whereIndex = indexOfKeyword(tokens, "WHERE");

        if (whereIndex == -1) {
            throw new RuntimeException("DELETE requires a WHERE clause.");
        }

        List<String> logicalOperators = new ArrayList<>();
        List<Condition> conditions = parseConditions(tokens, whereIndex, logicalOperators);

        DeleteExecutor delete = new DeleteExecutor();
        delete.execute(db, tableName, conditions, logicalOperators);
    }

    private void parseUpdate(List<String> tokens, DatabaseEngine db) {

        // UPDATE tableName SET columnName = newValue WHERE cond1 (AND|OR) cond2 ...
        String tableName = tokens.get(1);

        int setIndex = indexOfKeyword(tokens, "SET");
        int whereIndex = indexOfKeyword(tokens, "WHERE");

        if (setIndex == -1) {
            throw new RuntimeException("UPDATE requires a SET clause.");
        }

        if (whereIndex == -1) {
            throw new RuntimeException("UPDATE requires a WHERE clause.");
        }

        String updateColumn = tokens.get(setIndex + 1);
        Object newValue = parseValue(tokens.get(setIndex + 3)); // setIndex+2 is the '=' token

        List<String> logicalOperators = new ArrayList<>();
        List<Condition> conditions = parseConditions(tokens, whereIndex, logicalOperators);

        UpdateExecutor update = new UpdateExecutor();
        update.execute(db, tableName, updateColumn, newValue, conditions, logicalOperators);
    }

    private void parseDropTable(List<String> tokens, DatabaseEngine db) {

        // DROP TABLE tableName
        String tableName = tokens.get(2);

        db.dropTable(tableName);

        System.out.println("Table '" + tableName + "' dropped successfully.");
    }

    // Finds the index of a keyword (case-insensitive) in the token list,
    // e.g. locating "WHERE" or "SET" regardless of surrounding clause shape.
    // Returns -1 if the keyword isn't present.
    private int indexOfKeyword(List<String> tokens, String keyword) {

        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).equalsIgnoreCase(keyword)) {
                return i;
            }
        }

        return -1;
    }

    // Parses everything after a WHERE keyword into a chain of Conditions
    // joined by AND/OR, e.g. "id > 5 AND salary < 200000" becomes two
    // Conditions with logicalOperators = ["AND"]. logicalOperators is an
    // out-parameter: the caller passes in an empty list to be filled in,
    // since a WHERE clause needs both the conditions and the joins between
    // them, and Java doesn't have a clean way to return two values at once.
    private List<Condition> parseConditions(List<String> tokens, int whereIndex, List<String> logicalOperators) {

        List<Condition> conditions = new ArrayList<>();

        int i = whereIndex + 1;

        while (i < tokens.size()) {

            String columnName = tokens.get(i);
            String operator = tokens.get(i + 1);
            Object value = parseValue(tokens.get(i + 2));

            conditions.add(new Condition(columnName, operator, value));
            i += 3;

            if (i < tokens.size()) {

                String logicalOperator = tokens.get(i).toUpperCase();

                if (!logicalOperator.equals("AND") && !logicalOperator.equals("OR")) {
                    throw new RuntimeException(
                            "Expected AND or OR but found '" + tokens.get(i) + "'."
                    );
                }

                logicalOperators.add(logicalOperator);
                i++;
            }
        }

        return conditions;
    }

    // Converts a raw token into the value it represents:
    // - a quoted token ('Alice') becomes the String "Alice"
    // - an unquoted numeric token (42) becomes an Integer
    // - anything else is kept as a plain String
    private Object parseValue(String token) {

        if (token.length() >= 2 && token.startsWith("'") && token.endsWith("'")) {
            return token.substring(1, token.length() - 1);
        }

        try {
            return Integer.parseInt(token);
        }
        catch (NumberFormatException e) {
            return token;
        }
    }
}
