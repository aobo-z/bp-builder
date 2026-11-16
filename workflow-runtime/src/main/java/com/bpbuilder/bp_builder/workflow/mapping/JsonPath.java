package com.bpbuilder.bp_builder.workflow.mapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The small path language used by reviewed mappings: {@code $.a.b}, {@code a.b},
 * {@code a[0]}, {@code a[*].b} and {@code a['key with spaces']}. A wildcard
 * reads as a list with one entry per array element; writing a list through a
 * wildcard path writes one element per list entry.
 */
public final class JsonPath {

    private JsonPath() {
    }

    record Token(String key, Integer index, boolean wildcard) {
        static final Token WILDCARD = new Token(null, null, true);

        static Token key(String key) {
            return new Token(key, null, false);
        }

        static Token index(int index) {
            return new Token(null, index, false);
        }
    }

    public static boolean isRoot(String path) {
        return path == null || path.isBlank() || "$".equals(path.trim());
    }

    public static boolean hasWildcard(String path) {
        return path != null && path.contains("[*]");
    }

    public static Object read(Object root, String path) {
        return read(root, parse(path), 0);
    }

    /** Writes into a JSON object tree, creating intermediate objects and arrays. */
    public static void write(Map<String, Object> root, String path, Object value) {
        List<Token> tokens = parse(path);
        if (tokens.isEmpty()) throw new MappingException(path, "cannot write to the document root");
        writeTokens(root, tokens, value, path);
    }

    static List<Token> parse(String path) {
        if (path == null) throw new MappingException(null, "path is null");
        String text = path.trim();
        List<Token> tokens = new ArrayList<>();
        int i = text.startsWith("$") ? 1 : 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '[') {
                int end = text.indexOf(']', i);
                if (end < 0) throw new MappingException(path, "unclosed '['");
                String inner = text.substring(i + 1, end).trim();
                if ("*".equals(inner)) {
                    tokens.add(Token.WILDCARD);
                } else if (!inner.isEmpty() && inner.chars().allMatch(Character::isDigit)) {
                    tokens.add(Token.index(Integer.parseInt(inner)));
                } else if (inner.length() >= 2 && (inner.startsWith("'") && inner.endsWith("'")
                        || inner.startsWith("\"") && inner.endsWith("\""))) {
                    tokens.add(Token.key(inner.substring(1, inner.length() - 1)));
                } else {
                    throw new MappingException(path, "unsupported bracket expression [" + inner + "]");
                }
                i = end + 1;
            } else {
                if (c == '.') i++;
                int start = i;
                while (i < text.length() && text.charAt(i) != '.' && text.charAt(i) != '[') i++;
                if (start == i) throw new MappingException(path, "empty segment");
                tokens.add(Token.key(text.substring(start, i)));
            }
        }
        return tokens;
    }

    private static Object read(Object node, List<Token> tokens, int position) {
        if (position == tokens.size()) return node;
        if (node == null) return null;
        Token token = tokens.get(position);
        if (token.wildcard()) {
            if (!(node instanceof List<?> list)) return null;
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) result.add(read(item, tokens, position + 1));
            return result;
        }
        if (token.index() != null) {
            if (!(node instanceof List<?> list) || token.index() >= list.size()) return null;
            return read(list.get(token.index()), tokens, position + 1);
        }
        if (!(node instanceof Map<?, ?> map)) return null;
        return read(map.get(token.key()), tokens, position + 1);
    }

    private static void writeTokens(Object root, List<Token> tokens, Object value, String path) {
        int wildcard = -1;
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).wildcard()) {
                wildcard = i;
                break;
            }
        }
        if (wildcard >= 0) {
            if (!(value instanceof List<?> values)) {
                throw new MappingException(path, "a wildcard path needs one value per element, got a single value");
            }
            for (int index = 0; index < values.size(); index++) {
                if (values.get(index) == null) continue;
                List<Token> concrete = new ArrayList<>(tokens);
                concrete.set(wildcard, Token.index(index));
                writeTokens(root, concrete, values.get(index), path);
            }
            return;
        }
        Object current = root;
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            boolean last = i == tokens.size() - 1;
            Token next = last ? null : tokens.get(i + 1);
            current = step(current, token, last, next, value, path);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object step(Object current, Token token, boolean last, Token next, Object value, String path) {
        if (token.key() != null) {
            if (!(current instanceof Map<?, ?> raw)) throw new MappingException(path, "expected an object at '" + token.key() + "'");
            Map<String, Object> map = (Map<String, Object>) raw;
            if (last) {
                map.put(token.key(), value);
                return null;
            }
            return map.computeIfAbsent(token.key(), ignored -> container(next));
        }
        if (!(current instanceof List<?> raw)) throw new MappingException(path, "expected an array at [" + token.index() + "]");
        List<Object> list = (List<Object>) raw;
        while (list.size() <= token.index()) list.add(null);
        if (last) {
            list.set(token.index(), value);
            return null;
        }
        if (list.get(token.index()) == null) list.set(token.index(), container(next));
        return list.get(token.index());
    }

    private static Object container(Token next) {
        return next.key() != null ? new LinkedHashMap<String, Object>() : new ArrayList<>();
    }
}
