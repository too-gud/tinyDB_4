package storage;
import java.util.*;
public class Row {

    private List<Object> values;

    public Row(List<Object> values) {
        this.values = values;
    }

    public List<Object> getValues() {
        return values;
    }

    @Override
    public String toString() {
        return values.toString();
    }
}