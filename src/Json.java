import java.util.Map;

/** Small JSON output helper for benchmark data; no external library required. */
final class Json {
    private Json() {}
    static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return quote(text);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder result = new StringBuilder("{");
            for (var entry : map.entrySet()) {
                if (result.length() > 1) result.append(',');
                result.append('\n').append(quote(entry.getKey().toString())).append(':')
                        .append(stringify(entry.getValue()));
            }
            return result.append("\n}").toString();
        }
        if (value instanceof Iterable<?> values) {
            StringBuilder result = new StringBuilder("[");
            for (Object element : values) {
                if (result.length() > 1) result.append(',');
                result.append(stringify(element));
            }
            return result.append(']').toString();
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
    }
    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 32) result.append(String.format("\\u%04x", (int) character));
                    else result.append(character);
                }
            }
        }
        return result.append('"').toString();
    }
}
