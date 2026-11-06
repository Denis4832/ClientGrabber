package local.logger;

import java.lang.reflect.Array;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small JSON writer for log values; no third-party runtime dependencies. */
public final class Json {
    private Json() { }

    public static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return result;
    }

    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        append(out, value);
        return out.toString();
    }

    private static void append(StringBuilder out, Object value) {
        if (value == null) { out.append("null"); return; }
        if (value instanceof Number n) {
            if ((n instanceof Double && !Double.isFinite(n.doubleValue()))
                    || (n instanceof Float && !Float.isFinite(n.floatValue()))) out.append("null");
            else out.append(n);
        } else if (value instanceof Boolean) out.append(value);
        else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                quote(out, String.valueOf(entry.getKey())); out.append(':'); append(out, entry.getValue());
                first = false;
            }
            out.append('}');
        } else if (value instanceof Iterable<?> values) {
            out.append('['); boolean first = true;
            for (Object item : values) { if (!first) out.append(','); append(out, item); first = false; }
            out.append(']');
        } else if (value.getClass().isArray()) {
            out.append('[');
            for (int i = 0; i < Array.getLength(value); i++) { if (i > 0) out.append(','); append(out, Array.get(value, i)); }
            out.append(']');
        } else quote(out, String.valueOf(value));
    }

    private static void quote(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }
}
