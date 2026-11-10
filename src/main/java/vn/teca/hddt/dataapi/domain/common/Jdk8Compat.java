package vn.teca.hddt.dataapi.domain.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Thay thế các API Java 9+ (List/Set/Map.of, copyOf, Stream.toList, String.isBlank, HexFormat, InputStream.readAllBytes /
 * transferTo) khi chạy trên Java 8 — giữ nguyên hành vi mà mã nghiệp vụ dựa vào: tập hợp bất biến, từ chối phần tử/khóa null
 * (NPE) và khóa trùng (IllegalArgumentException) như bản gốc. Khác biệt duy nhất: thứ tự duyệt Set/Map theo thứ tự chèn
 * (LinkedHash*) thay vì thứ tự ngẫu nhiên theo phiên JVM của Set.of/Map.of — mã không được dựa vào thứ tự đó.
 */
public final class Jdk8Compat {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Jdk8Compat() {
    }

    /** {@code List.of(...)}. */
    @SafeVarargs
    public static <E> List<E> listOf(E... elements) {
        for (E e : elements) {
            Objects.requireNonNull(e);
        }
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(elements)));
    }

    /** {@code List.copyOf(c)}. */
    public static <E> List<E> listCopyOf(Collection<? extends E> c) {
        List<E> copy = new ArrayList<>(c.size());
        for (E e : c) {
            copy.add(Objects.requireNonNull(e));
        }
        return Collections.unmodifiableList(copy);
    }

    /** {@code Set.of(...)}: phần tử trùng ⇒ IllegalArgumentException. */
    @SafeVarargs
    public static <E> Set<E> setOf(E... elements) {
        Set<E> s = new LinkedHashSet<>();
        for (E e : elements) {
            if (!s.add(Objects.requireNonNull(e))) {
                throw new IllegalArgumentException("duplicate element: " + e);
            }
        }
        return Collections.unmodifiableSet(s);
    }

    /** {@code Set.copyOf(c)}: phần tử trùng được gộp. */
    public static <E> Set<E> setCopyOf(Collection<? extends E> c) {
        Set<E> s = new LinkedHashSet<>();
        for (E e : c) {
            s.add(Objects.requireNonNull(e));
        }
        return Collections.unmodifiableSet(s);
    }

    /** {@code Map.of()}. */
    public static <K, V> Map<K, V> mapOf() {
        return Collections.emptyMap();
    }

    /** {@code Map.of(k1, v1)}. */
    public static <K, V> Map<K, V> mapOf(K k1, V v1) {
        return entries(k1, v1);
    }

    /** {@code Map.of(k1, v1, k2, v2)}. */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2) {
        return entries(k1, v1, k2, v2);
    }

    /** {@code Map.of(k1, v1, k2, v2, k3, v3)}. */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3) {
        return entries(k1, v1, k2, v2, k3, v3);
    }

    /** {@code Map.of(k1, v1, ..., k4, v4)}. */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        return entries(k1, v1, k2, v2, k3, v3, k4, v4);
    }

    /** {@code Map.of(k1, v1, ..., k5, v5)}. */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        return entries(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5);
    }


    /** {@code Map.of(k1, v1, ..., k6, v6)}. */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6) {
        return entries(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6);
    }

    /** {@code Map.of(k1, v1, ..., k7, v7)}. */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6,
            K k7, V v7) {
        return entries(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7);
    }

    /** {@code Map.of(k1, v1, ..., k8, v8)}. */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6,
            K k7, V v7, K k8, V v8) {
        return entries(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8);
    }

    /** {@code Map.of(k1, v1, ..., k9, v9)}. */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6,
            K k7, V v7, K k8, V v8, K k9, V v9) {
        return entries(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8, k9, v9);
    }

    /** {@code Map.of(k1, v1, ..., k10, v10)}. */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6,
            K k7, V v7, K k8, V v8, K k9, V v9, K k10, V v10) {
        return entries(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8, k9, v9, k10, v10);
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> entries(Object... kv) {
        Map<K, V> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            K k = (K) Objects.requireNonNull(kv[i]);
            V v = (V) Objects.requireNonNull(kv[i + 1]);
            if (m.put(k, v) != null) {
                throw new IllegalArgumentException("duplicate key: " + k);
            }
        }
        return Collections.unmodifiableMap(m);
    }

    /** {@code Map.copyOf(m)}. */
    public static <K, V> Map<K, V> mapCopyOf(Map<? extends K, ? extends V> source) {
        Map<K, V> m = new LinkedHashMap<>();
        for (Map.Entry<? extends K, ? extends V> e : source.entrySet()) {
            m.put(Objects.requireNonNull(e.getKey()), Objects.requireNonNull(e.getValue()));
        }
        return Collections.unmodifiableMap(m);
    }

    /** {@code Stream.toList()}: danh sách bất biến, cho phép phần tử null. */
    public static <T> Collector<T, ?, List<T>> toList() {
        return Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList);
    }

    /** {@code s.isBlank()}: rỗng hoặc chỉ gồm code point khoảng trắng ({@link Character#isWhitespace(int)}); s null ⇒ NPE. */
    public static boolean isBlank(String s) {
        int i = 0;
        int n = s.length();
        while (i < n) {
            int cp = s.codePointAt(i);
            if (!Character.isWhitespace(cp)) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return true;
    }

    /** {@code HexFormat.of().formatHex(bytes)} (chữ thường). */
    public static String formatHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[2 * i] = HEX[(bytes[i] >> 4) & 0xF];
            out[2 * i + 1] = HEX[bytes[i] & 0xF];
        }
        return new String(out);
    }

    /** {@code HexFormat.of().parseHex(s)}: độ dài lẻ hoặc ký tự không phải hex ⇒ IllegalArgumentException. */
    public static byte[] parseHex(String s) {
        if (s.length() % 2 != 0) {
            throw new IllegalArgumentException("string length not even: " + s.length());
        }
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(s.charAt(2 * i), 16);
            int lo = Character.digit(s.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0 || s.charAt(2 * i) > 0x7F || s.charAt(2 * i + 1) > 0x7F) {
                throw new IllegalArgumentException("not a hexadecimal digit at index " + (2 * i));
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    /** {@code in.readAllBytes()}. */
    public static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** {@code in.transferTo(OutputStream.nullOutputStream())}: đọc hết phần còn lại, trả số byte đã bỏ qua. */
    public static long drain(InputStream in) throws IOException {
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
        }
        return total;
    }
}
