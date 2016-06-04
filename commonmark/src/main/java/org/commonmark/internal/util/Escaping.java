package org.commonmark.internal.util;

import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Escaping {

    public static final String ESCAPABLE = "[!\"#$%&\'()*+,./:;<=>?@\\[\\\\\\]^_`{|}~-]";

    private static final String ENTITY = "&(?:#x[a-f0-9]{1,8}|#[0-9]{1,8}|[a-z][a-z0-9]{1,31});";

    private static final Pattern BACKSLASH_OR_AMP = Pattern.compile("[\\\\&]");

    private static final Pattern ENTITY_OR_ESCAPED_CHAR =
            Pattern.compile("\\\\" + ESCAPABLE + '|' + ENTITY, Pattern.CASE_INSENSITIVE);

    private static final String XML_SPECIAL = "[&<>\"]";

    private static final Pattern XML_SPECIAL_RE = Pattern.compile(XML_SPECIAL);

    private static final Pattern XML_SPECIAL_OR_ENTITY =
            Pattern.compile(ENTITY + '|' + XML_SPECIAL, Pattern.CASE_INSENSITIVE);

    // From RFC 3986 (see "reserved", "unreserved") except don't escape '[' or ']' to be compatible with JS encodeURI
    private static final Pattern ESCAPE_IN_URI =
            Pattern.compile("(%[a-fA-F0-9]{0,2}|[^:/?#@!$&'()*+,;=a-zA-Z0-9\\-._~])");

    private static final char[] HEX_DIGITS =
            new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

    private static final Pattern WHITESPACE = Pattern.compile("[ \t\r\n]+");

    private static final Replacer UNSAFE_CHAR_REPLACER = new Replacer() {
        @Override
        public void replace(String input, StringBuilder sb) {
            switch (input) {
                case "&":
                    sb.append("&amp;");
                    break;
                case "<":
                    sb.append("&lt;");
                    break;
                case ">":
                    sb.append("&gt;");
                    break;
                case "\"":
                    sb.append("&quot;");
                    break;
                default:
                    sb.append(input);
            }
        }
    };

    private static final Replacer UNESCAPE_REPLACER = new Replacer() {
        @Override
        public void replace(String input, StringBuilder sb) {
            if (input.charAt(0) == '\\') {
                sb.append(input, 1, input.length());
            } else {
                sb.append(Html5Entities.entityToString(input));
            }
        }
    };

    private static final Replacer URI_REPLACER = new Replacer() {
        @Override
        public void replace(String input, StringBuilder sb) {
            if (input.startsWith("%")) {
                if (input.length() == 3) {
                    // Already percent-encoded, preserve
                    sb.append(input);
                } else {
                    // %25 is the percent-encoding for %
                    sb.append("%25");
                    sb.append(input, 1, input.length());
                }
            } else {
                byte[] bytes = input.getBytes(Charset.forName("UTF-8"));
                for (byte b : bytes) {
                    sb.append('%');
                    sb.append(HEX_DIGITS[(b >> 4) & 0xF]);
                    sb.append(HEX_DIGITS[b & 0xF]);
                }
            }
        }
    };

    public static String escapeHtml(String input, boolean preserveEntities) {
        Pattern p = preserveEntities ? XML_SPECIAL_OR_ENTITY : XML_SPECIAL_RE;
        return replaceAll(p, input, UNSAFE_CHAR_REPLACER);
    }

    /**
     * Replace entities and backslash escapes with literal characters.
     */
    public static String unescapeString(String s) {
        if (BACKSLASH_OR_AMP.matcher(s).find()) {
            return replaceAll(ENTITY_OR_ESCAPED_CHAR, s, UNESCAPE_REPLACER);
        } else {
            return s;
        }
    }

    public static String percentEncodeUrl(String s) {
        return replaceAll(ESCAPE_IN_URI, s, URI_REPLACER);
    }

    public static String extractReference(CharSequence input) {
        // Strip '[' and ']', then trim and convert to lowercase
        int stripEnd = input.charAt(input.length() - 1) == ':' ? 2 : 1;
        return Escaping.collapseWhitespace(input.subSequence(1, input.length() - stripEnd).toString(), true);
    }

    public static String normalizeReference(CharSequence input) {
        // Strip '[' and ']', then trim and convert to lowercase
        return extractReference(input).toLowerCase();
    }

    public static String collapseWhitespace(CharSequence input, boolean trim) {
        StringBuilder sb = new StringBuilder(input.length());
        int iMax = input.length();
        boolean hadSpace = false;

        for (int i = 0; i < iMax; i++) {
            char c = input.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                hadSpace = true;
            } else {
                if (hadSpace && (!trim || sb.length() > 0)) sb.append(' ');
                sb.append(c);
                hadSpace = false;
            }
        }
        if (hadSpace && !trim) sb.append(' ');
        return sb.toString();
    }

    public static String normalizeEOL(CharSequence input) {
        StringBuilder sb = new StringBuilder(input.length());
        int iMax = input.length();
        boolean hadCr = false;

        for (int i = 0; i < iMax; i++) {
            char c = input.charAt(i);
            if (c == '\r') {
                //sb.append("\n");
                //hadCr = true;
            } else if (c == '\n') {
                if (!hadCr) sb.append("\n");
                hadCr = false;
            } else {
                sb.append(c);
                hadCr = false;
            }
        }
        return sb.toString();
    }

    private static String replaceAll(Pattern p, String s, Replacer replacer) {
        Matcher matcher = p.matcher(s);

        if (!matcher.find()) {
            return s;
        }

        StringBuilder sb = new StringBuilder(s.length() + 16);
        int lastEnd = 0;
        do {
            sb.append(s, lastEnd, matcher.start());
            replacer.replace(matcher.group(), sb);
            lastEnd = matcher.end();
        } while (matcher.find());

        if (lastEnd != s.length()) {
            sb.append(s, lastEnd, s.length());
        }
        return sb.toString();
    }

    private interface Replacer {
        void replace(String input, StringBuilder sb);
    }
}
