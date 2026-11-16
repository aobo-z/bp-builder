package com.bpbuilder.bp_builder.workflow.mapping;

import com.bpbuilder.bp_builder.canonical.MappingTransform;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The fixed set of reviewed value conversions. Each one either produces a
 * correct value or throws {@link MappingException}; none of them passes an
 * unrecognised value through unchanged.
 */
public final class ValueTransforms {

    private static final Pattern PLAIN_DECIMAL = Pattern.compile("[+-]?\\d+(\\.\\d+)?");
    private static final Map<String, String> COUNTRY_CODES = countryCodes();

    private ValueTransforms() {
    }

    public static Object apply(MappingTransform transform, Object value, Map<String, Object> config, String field) {
        if (transform == MappingTransform.CONSTANT) return config.get("value");
        if (value instanceof List<?> values) {
            List<Object> result = new ArrayList<>(values.size());
            for (Object item : values) result.add(item == null ? null : apply(transform, item, config, field));
            return result;
        }
        if (value == null) return null;
        return switch (transform) {
            case DIRECT, FIRST_NON_NULL, ARRAY_ITEM, ENVIRONMENT -> value;
            case DECIMAL -> decimal(value, field);
            case DATE_FORMAT -> date(value, config, field);
            case COUNTRY_NAME_TO_ISO2 -> country(value, field);
            case REMOVE_WHITESPACE -> String.valueOf(value).replaceAll("\\s+", "");
            case ARTIFACT -> encode(value, config.get("encodings"), field);
            case CONSTANT -> config.get("value");
        };
    }

    static BigDecimal decimal(Object value, String field) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return new BigDecimal(number.toString());
        String text = String.valueOf(value).trim();
        if (!PLAIN_DECIMAL.matcher(text).matches()) {
            throw new MappingException(field, "'" + text + "' is not a plain decimal number");
        }
        return new BigDecimal(text);
    }

    static String date(Object value, Map<String, Object> config, String field) {
        String input = String.valueOf(config.getOrDefault("input", "YYYY-MM-DD"));
        String output = String.valueOf(config.getOrDefault("output", "YYYY-MM-DD"));
        String text = String.valueOf(value).trim();
        try {
            LocalDate date = LocalDate.parse(text, formatter(input));
            return date.format(formatter(output));
        } catch (DateTimeParseException | IllegalArgumentException error) {
            throw new MappingException(field, "'" + text + "' does not match the date format " + input);
        }
    }

    static String country(Object value, String field) {
        String text = String.valueOf(value).trim();
        String code = COUNTRY_CODES.get(text.toLowerCase(Locale.ROOT));
        if (code == null) throw new MappingException(field, "'" + text + "' is not a recognised country name or ISO 3166-1 code");
        return code;
    }

    static String encode(Object value, Object encodings, String field) {
        String text = String.valueOf(value);
        if (encodings == null) return text;
        List<?> steps = encodings instanceof List<?> list ? list : List.of(encodings);
        for (Object step : steps) {
            String name = String.valueOf(step).trim().toUpperCase(Locale.ROOT);
            text = switch (name) {
                case "BASE64" -> Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
                case "MD5_HEX" -> md5Hex(text);
                default -> throw new MappingException(field, "unsupported encoding " + name);
            };
        }
        return text;
    }

    private static String md5Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("MD5 is not available", error);
        }
    }

    /** Accepts the reviewed pattern notation (YYYY, MM, DD) used in transform_config. */
    private static DateTimeFormatter formatter(String pattern) {
        String java = pattern.replace("YYYY", "uuuu").replace("DD", "dd");
        return DateTimeFormatter.ofPattern(java, Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    }

    private static Map<String, String> countryCodes() {
        Map<String, String> codes = new java.util.HashMap<>();
        for (String code : Locale.getISOCountries()) {
            codes.put(code.toLowerCase(Locale.ROOT), code);
            String name = new Locale("", code).getDisplayCountry(Locale.ENGLISH);
            if (!name.isBlank()) codes.put(name.toLowerCase(Locale.ROOT), code);
        }
        return Map.copyOf(codes);
    }
}
