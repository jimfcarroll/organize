import java.util.*;
import java.util.function.BiFunction;

public class JsonToList {

    public static <T> List<T> convert(
            Map<String, Object> jsonMap,
            BiFunction<String, Object, T> mapper) {

        List<T> result = new ArrayList<>();
        for (Map.Entry<String, Object> entry : jsonMap.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map || value instanceof List) {
                throw new IllegalArgumentException(
                    "Nested Map/List not allowed for key: " + entry.getKey()
                );
            }
            result.add(mapper.apply(entry.getKey(), value));
        }
        return result;
    }

    // Example usage
    public static void main(String[] args) {
        record KeyValuePair(String key, Object value) {}

        Map<String, Object> sample = new HashMap<>();
        sample.put("field1", "value1");
        sample.put("field2", 1);
        sample.put("field3", true);
        sample.put("field4", null);

        List<KeyValuePair> pairs = convert(sample, KeyValuePair::new);

        pairs.forEach(System.out::println);
    }
}
