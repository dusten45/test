package android.util;

import java.nio.charset.StandardCharsets;

public final class Base64 {
    public static final int DEFAULT = 0, NO_PADDING = 1, NO_WRAP = 2, CRLF = 4, URL_SAFE = 8, NO_CLOSE = 16;
    private Base64() { }
    public static byte[] decode(String input, int flags) {
        String compact = input.replaceAll("\\s", "");
        return ((flags & URL_SAFE) != 0 ? java.util.Base64.getUrlDecoder() : java.util.Base64.getDecoder()).decode(compact);
    }
    public static byte[] decode(byte[] input, int flags) { return decode(new String(input, StandardCharsets.US_ASCII), flags); }
    public static byte[] decode(byte[] input, int offset, int length, int flags) {
        return decode(new String(input, offset, length, StandardCharsets.US_ASCII), flags);
    }
    public static String encodeToString(byte[] input, int flags) {
        java.util.Base64.Encoder encoder = (flags & URL_SAFE) != 0 ? java.util.Base64.getUrlEncoder() : java.util.Base64.getEncoder();
        if ((flags & NO_PADDING) != 0) { encoder = encoder.withoutPadding(); }
        String output = encoder.encodeToString(input);
        if ((flags & NO_WRAP) != 0 || output.isEmpty()) { return output; }
        String newline = (flags & CRLF) != 0 ? "\r\n" : "\n";
        StringBuilder wrapped = new StringBuilder();
        for (int start = 0; start < output.length(); start += 76) {
            wrapped.append(output, start, Math.min(start + 76, output.length())).append(newline);
        }
        return wrapped.toString();
    }
    public static byte[] encode(byte[] input, int flags) { return encodeToString(input, flags).getBytes(StandardCharsets.US_ASCII); }
    public static byte[] encode(byte[] input, int offset, int length, int flags) {
        return encode(java.util.Arrays.copyOfRange(input, offset, offset + length), flags);
    }
}
