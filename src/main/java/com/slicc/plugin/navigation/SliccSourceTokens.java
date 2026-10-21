package com.slicc.plugin.navigation;

import com.intellij.openapi.progress.ProgressManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SliccSourceTokens {

    record Token(String text, int offset, boolean literal) {}
    private record Conditional(boolean parent, boolean taken) {}
    private static final Pattern INCLUDE = Pattern.compile("^#\\s*include\\s*[\"<]([^\">]+)[\">]");
    private static final List<String> RAW_PREFIXES = List.of("R\"", "u8R\"", "uR\"", "UR\"", "LR\"");

    private SliccSourceTokens() {}

    static List<Token> read(String source, boolean python) {
        List<Token> tokens = new ArrayList<>();
        Deque<Conditional> conditions = new ArrayDeque<>();
        boolean active = true;
        int offset = 0;
        while (offset < source.length()) {
            ProgressManager.checkCanceled();
            char current = source.charAt(offset);
            if (Character.isWhitespace(current)) { offset++; continue; }
            if (current == '#' && (python || lineStart(source, offset))) {
                int end = python ? source.indexOf('\n', offset) : logicalLineEnd(source, offset);
                if (end < 0) end = source.length();
                String line = source.substring(offset, end).replaceAll("\\\\\\r?\\n", "");
                String directive = line.replaceFirst("^#\\s*", "").trim();
                if (!python) {
                    if (directive.matches("(?:if|ifdef|ifndef)\\b.*")) {
                        Boolean value = condition(directive);
                        conditions.push(new Conditional(active, Boolean.TRUE.equals(value)));
                        active = active && !Boolean.FALSE.equals(value);
                    } else if (directive.matches("elif\\b.*") && !conditions.isEmpty()) {
                        Conditional previous = conditions.pop();
                        Boolean value = condition(directive);
                        active = previous.parent && !previous.taken && !Boolean.FALSE.equals(value);
                        conditions.push(new Conditional(previous.parent, previous.taken || Boolean.TRUE.equals(value)));
                    } else if (directive.matches("else\\b.*") && !conditions.isEmpty()) {
                        Conditional previous = conditions.pop();
                        active = previous.parent && !previous.taken;
                        conditions.push(new Conditional(previous.parent, true));
                    } else if (directive.matches("endif\\b.*") && !conditions.isEmpty()) {
                        active = conditions.pop().parent;
                    } else if (active) {
                        Matcher include = INCLUDE.matcher(line);
                        if (include.find()) {
                            tokens.add(new Token("#include", offset, false));
                            int pathOffset = source.indexOf(include.group(1), offset);
                            tokens.add(new Token(include.group(1), pathOffset >= offset && pathOffset < end ? pathOffset : offset, true));
                        }
                    }
                }
                offset = end;
                continue;
            }
            if (!python && source.startsWith("//", offset)) {
                offset = logicalLineEnd(source, offset + 2);
                continue;
            }
            if (!python && source.startsWith("/*", offset)) {
                int end = source.indexOf("*/", offset + 2);
                offset = end < 0 ? source.length() : end + 2;
                continue;
            }
            if (!python) {
                int rawEnd = rawStringEnd(source, offset);
                if (rawEnd >= 0) {
                    offset = rawEnd;
                    continue;
                }
            }
            if (current == '\'' || current == '"') {
                int start = offset++;
                String delimiter = String.valueOf(current);
                if (python && source.startsWith(delimiter.repeat(3), start)) {
                    int end = source.indexOf(delimiter.repeat(3), start + 3);
                    offset = end < 0 ? source.length() : end + 3;
                    continue;
                }
                StringBuilder value = new StringBuilder();
                while (offset < source.length() && source.charAt(offset) != current) {
                    char character = source.charAt(offset++);
                    if (character == '\\' && offset < source.length()) character = source.charAt(offset++);
                    value.append(character);
                }
                if (offset < source.length()) offset++;
                if (active) tokens.add(new Token(value.toString(), start, true));
                continue;
            }
            if (!active) { offset++; continue; }
            int start = offset++;
            if (Character.isJavaIdentifierStart(current) || Character.isDigit(current)) {
                while (offset < source.length() && Character.isJavaIdentifierPart(source.charAt(offset))) offset++;
            } else if (current == ':' && offset < source.length() && source.charAt(offset) == ':') {
                offset++;
            }
            tokens.add(new Token(source.substring(start, offset), start, false));
        }
        return tokens;
    }

    static boolean identifier(Token token) {
        return !token.literal && !token.text.isEmpty() && Character.isJavaIdentifierStart(token.text.charAt(0));
    }

    private static Boolean condition(String directive) {
        if (!directive.matches("(?:if|elif)\\b.*")) return null;
        String expression = directive.replaceFirst("^(?:if|elif)\\b", "")
                .replaceAll("/\\*.*?\\*/", " ").replaceFirst("//.*$", "").trim();
        while (expression.startsWith("(") && expression.endsWith(")")) {
            expression = expression.substring(1, expression.length() - 1).trim();
        }
        if (!expression.matches("[+-]?(?:0[xX][0-9a-fA-F]+|0[bB][01]+|[0-9]+)[uUlL]*")) return null;
        String digits = expression.replaceFirst("^[+-]", "").replaceFirst("^0[xXbB]", "").replaceFirst("[uUlL]+$", "");
        return !digits.matches("0+");
    }

    private static int logicalLineEnd(String source, int start) {
        int end = source.indexOf('\n', start);
        while (end >= 0) {
            int previous = end - 1;
            if (previous >= start && source.charAt(previous) == '\r') previous--;
            if (previous < start || source.charAt(previous) != '\\') return end;
            end = source.indexOf('\n', end + 1);
        }
        return source.length();
    }

    private static int rawStringEnd(String source, int start) {
        for (String prefix : RAW_PREFIXES) {
            if (!source.startsWith(prefix, start)) continue;
            int delimiterStart = start + prefix.length();
            int opening = source.indexOf('(', delimiterStart);
            if (opening < 0 || opening - delimiterStart > 16) return -1;
            String delimiter = source.substring(delimiterStart, opening);
            if (delimiter.chars().anyMatch(character -> Character.isWhitespace(character) || character == ')' || character == '\\')) return -1;
            String closing = ")" + delimiter + "\"";
            int end = source.indexOf(closing, opening + 1);
            return end < 0 ? source.length() : end + closing.length();
        }
        return -1;
    }

    private static boolean lineStart(String text, int offset) {
        for (int index = offset - 1; index >= 0 && text.charAt(index) != '\n'; index--) {
            if (!Character.isWhitespace(text.charAt(index))) return false;
        }
        return true;
    }
}
