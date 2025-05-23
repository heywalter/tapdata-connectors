package io.tapdata.kit;

import org.apache.commons.lang3.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StringKit {

    /**
     * write string several times
     *
     * @param copied   "?"
     * @param count    3
     * @param combiner ","
     * @return "?,?,?"
     */
    public static String copyString(String copied, Integer count, String combiner) {
        if (count < 1 || EmptyKit.isNull(copied)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(copied).append(combiner);
        }
        return sb.delete(sb.length() - combiner.length(), sb.length()).toString();
    }

    //replace
    public static String replace(String text, String searchString, String replacement) {
        if (EmptyKit.isEmpty(text)) {
            return "";
        }
        return StringUtils.replace(text, searchString, replacement);
    }

    public static String replaceEscape(String input, char[] replacements) {
        char[] chars = input.toCharArray();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < chars.length) {
            char c = chars[i];
            for (char replacement : replacements) {
                if (c == replacement) {
                    sb.append("\\");
                    break;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /**
     * join strings with around and splitter
     *
     * @param list     ["a","b","c"]
     * @param around   "'"
     * @param splitter ","
     * @return "'a','b','c'"
     */
    public static String joinString(Collection<String> list, String around, String splitter) {
        if (EmptyKit.isEmpty(list)) {
            return "";
        }
        return list.stream().map(s -> around + s.replace(around, around + around) + around).collect(Collectors.joining(splitter));
    }

    public static int compareVersion(String version1, String version2) {
        List<String> list1 = Arrays.stream(version1.split("\\.")).collect(Collectors.toList());
        List<String> list2 = Arrays.stream(version2.split("\\.")).collect(Collectors.toList());
        Iterator<String> iterator1 = list1.iterator();
        Iterator<String> iterator2 = list2.iterator();
        while (iterator1.hasNext() && iterator2.hasNext()) {
            String str1 = iterator1.next();
            String str2 = iterator2.next();
            if (Integer.parseInt(str1) > Integer.parseInt(str2)) {
                return 1;
            } else if (Integer.parseInt(str1) < Integer.parseInt(str2)) {
                return -1;
            }
        }
        if (iterator1.hasNext()) {
            return 1;
        } else if (iterator2.hasNext()) {
            return -1;
        } else {
            return 0;
        }
    }

    public static String subStringBetweenTwoString(String sql, String start, String end) {
        String sub;

        if (EmptyKit.isBlank(sql)) {
            return "";
        }

        int startIndex = indexOfIgnoreCase(sql, start);
        if (startIndex <= 0) {
            return "";
        }
        int endIndex = indexOfIgnoreCase(sql, end);
        if (endIndex <= 0) {
            return "";
        }

        if (startIndex >= endIndex) {
            throw new RuntimeException(String.format("Invalid sql: %s, start: %s, end: %s", sql, start, end));
        } else {
            sub = sql.substring(startIndex + start.length(), endIndex);
        }

        return sub;
    }

    public static int indexOfIgnoreCase(CharSequence str, CharSequence searchStr) {
        return indexOfIgnoreCase(str, searchStr, 0);
    }

    public static int indexOfIgnoreCase(CharSequence str, CharSequence searchStr, int startPos) {
        if (str != null && searchStr != null) {
            if (startPos < 0) {
                startPos = 0;
            }

            int endLimit = str.length() - searchStr.length() + 1;
            if (startPos > endLimit) {
                return -1;
            } else if (searchStr.length() == 0) {
                return startPos;
            } else {
                for (int i = startPos; i < endLimit; ++i) {
                    if (regionMatches(str, true, i, searchStr, 0, searchStr.length())) {
                        return i;
                    }
                }

                return -1;
            }
        } else {
            return -1;
        }
    }

    static boolean regionMatches(CharSequence cs, boolean ignoreCase, int thisStart, CharSequence substring, int start, int length) {
        if (cs instanceof String && substring instanceof String) {
            return ((String) cs).regionMatches(ignoreCase, thisStart, (String) substring, start, length);
        } else {
            int index1 = thisStart;
            int index2 = start;
            int tmpLen = length;
            int srcLen = cs.length() - thisStart;
            int otherLen = substring.length() - start;
            if (thisStart >= 0 && start >= 0 && length >= 0) {
                if (srcLen >= length && otherLen >= length) {
                    while (tmpLen-- > 0) {
                        char c1 = cs.charAt(index1++);
                        char c2 = substring.charAt(index2++);
                        if (c1 != c2) {
                            if (!ignoreCase) {
                                return false;
                            }

                            char u1 = Character.toUpperCase(c1);
                            char u2 = Character.toUpperCase(c2);
                            if (u1 != u2 && Character.toLowerCase(u1) != Character.toLowerCase(u2)) {
                                return false;
                            }
                        }
                    }

                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    public static boolean isLowerCase(String str) {
        if (EmptyKit.isNotEmpty(str)) {
            for (char c : str.toCharArray()) {
                if (Character.isUpperCase(c)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public static int indexOf(String str, String searchStr) {
        if (EmptyKit.isNotBlank(str) && EmptyKit.isNotBlank(searchStr)) {
            return str.indexOf(searchStr);
        }
        return -1;
    }

    public static String removeHeadTail(String str, String remove, Boolean upperCase) {
        if (EmptyKit.isBlank(str) || EmptyKit.isBlank(remove)) {
            return str;
        }
        if (str.startsWith(remove) && str.endsWith(remove) && str.length() >= 2 * remove.length()) {
            return str.substring(remove.length(), str.length() - remove.length());
        }
        if (EmptyKit.isNull(upperCase)) {
            return str;
        } else if (upperCase) {
            return str.toUpperCase();
        } else {
            return str.toLowerCase();
        }
    }

    public static boolean equalsIgnoreCase(String str1, String str2) {
        if (EmptyKit.isNull(str1) || EmptyKit.isNull(str2)) {
            return false;
        } else {
            return str1.equalsIgnoreCase(str2);
        }
    }

    public static String leftPad(String str, int size, String padStr) {
        if (str == null) {
            return null;
        } else {
            if (EmptyKit.isEmpty(padStr)) {
                padStr = " ";
            }

            int padLen = padStr.length();
            int strLen = str.length();
            int pads = size - strLen;
            if (pads <= 0) {
                return str;
            } else if (padLen == 1 && pads <= 8192) {
                return leftPad(str, size, padStr.charAt(0));
            } else if (pads == padLen) {
                return padStr.concat(str);
            } else if (pads < padLen) {
                return padStr.substring(0, pads).concat(str);
            } else {
                char[] padding = new char[pads];
                char[] padChars = padStr.toCharArray();

                for (int i = 0; i < pads; ++i) {
                    padding[i] = padChars[i % padLen];
                }

                return (new String(padding)).concat(str);
            }
        }
    }

    public static String leftPad(String str, int size, char padChar) {
        if (str == null) {
            return null;
        } else {
            int pads = size - str.length();
            if (pads <= 0) {
                return str;
            } else {
                return pads > 8192 ? leftPad(str, size, String.valueOf(padChar)) : repeat(padChar, pads).concat(str);
            }
        }
    }

    public static String repeat(char ch, int repeat) {
        if (repeat <= 0) {
            return "";
        } else {
            char[] buf = new char[repeat];
            Arrays.fill(buf, ch);
            return new String(buf);
        }
    }

    public static String removeLastReturn(String str) {
        if (str.endsWith("\r\n")) {
            return str.substring(0, str.length() - 2);
        } else if (str.endsWith("\r") || str.endsWith("\n")) {
            return str.substring(0, str.length() - 1);
        } else {
            return str;
        }
    }

    public static String md5(String str) {
        if (str == null || str.isEmpty()) return null;

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
            md.update(str.getBytes());
            byte[] hash = md.digest();

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                if ((255 & b) < 16) {
                    hexString.append("0").append(Integer.toHexString(255 & b));
                } else {
                    hexString.append(Integer.toHexString(255 & b));
                }
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException var6) {
            return null;
        }
    }

    public static boolean matchReg(String str, String reg) {
        String newReg = reg.replaceAll("\\*", ".*");
        Pattern pattern = Pattern.compile(newReg);
        return pattern.matcher(str).matches();
    }

    private static final char[] HEX_CHARS = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    public static String convertToHexString(byte[] toBeConverted) {
        if (toBeConverted == null) {
            throw new NullPointerException("Parameter to be converted can not be null");
        }
        char[] converted = new char[toBeConverted.length * 2];
        for (int i = 0; i < toBeConverted.length; i++) {
            byte b = toBeConverted[i];
            converted[i * 2] = HEX_CHARS[b >> 4 & 0x0F];
            converted[i * 2 + 1] = HEX_CHARS[b & 0x0F];
        }
        return String.valueOf(converted);
    }

    //正则表达式去除括号及括号内的内容
    public static String removeParentheses(String str) {
        return str.replaceAll("\\(.*?\\)", "");
    }

    public static byte[] toByteArray(String hex) {
        String hexString = hex.replace(" ", "");
        final byte[] byteArray = new byte[hexString.length() / 2];
        int k = 0;
        for (int i = 0; i < byteArray.length; i++) {
            byte high = (byte) (Character.digit(hexString.charAt(k), 16) & 0xff);
            byte low = (byte) (Character.digit(hexString.charAt(k + 1), 16) & 0xff);
            byteArray[i] = (byte) (high << 4 | low);
            k += 2;
        }
        return byteArray;
    }

    public static String trimTailBlank(Object str) {
        if (null == str) return null;
        return ("_" + str).trim().substring(1);
    }

    public static String escape(String name, String escapes) {
        String res = name;
        for (int i = 0; i < escapes.length(); i++) {
            char escape = escapes.charAt(i);
            res = escape(res, escape);
        }
        return res;
    }

    public static String escape(String name, char escape) {
        return name.replace(escape + "", "" + escape + escape);
    }

    private static final Pattern REGEX_SPECIAL_CHARS = Pattern.compile("[\\\\^$|*+?.,()\\[\\]{}]");

    public static String escapeRegex(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        Matcher matcher = REGEX_SPECIAL_CHARS.matcher(input);
        return matcher.replaceAll("\\\\$0");
    }
}
