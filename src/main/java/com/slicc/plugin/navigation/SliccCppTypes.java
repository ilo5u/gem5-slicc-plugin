package com.slicc.plugin.navigation;

import com.intellij.openapi.progress.ProgressManager;

import java.util.*;

final class SliccCppTypes {

    record Definition(String qualifiedName, int offset, int length) {}
    private record Scope(String name, boolean types) {}

    private SliccCppTypes() {}

    static List<Definition> parse(List<SliccSourceTokens.Token> tokens) {
        List<Definition> definitions = new ArrayList<>();
        Deque<Scope> scopes = new ArrayDeque<>();
        for (int index = 0; index < tokens.size(); index++) {
            ProgressManager.checkCanceled();
            SliccSourceTokens.Token token = tokens.get(index);
            String text = token.text();
            if (token.literal()) continue;
            if (text.equals("template") && at(tokens, index + 1, "<")) {
                index = balanced(tokens, index + 1, "<", ">");
                continue;
            }
            if (text.equals("namespace") && allowed(scopes)) {
                int cursor = index + 1;
                StringBuilder name = new StringBuilder();
                while (cursor < tokens.size() && (SliccSourceTokens.identifier(tokens.get(cursor)) || at(tokens, cursor, "::"))) {
                    name.append(tokens.get(cursor++).text());
                }
                if (at(tokens, cursor, "{")) {
                    scopes.addLast(new Scope(name.isEmpty() ? "<anonymous>" : name.toString(), true));
                    index = cursor;
                    continue;
                }
            }
            if ((text.equals("class") || text.equals("struct") || text.equals("union") || text.equals("enum")) && allowed(scopes)) {
                int cursor = index + 1;
                if (text.equals("enum") && (at(tokens, cursor, "class") || at(tokens, cursor, "struct"))) cursor++;
                cursor = skipAttributes(tokens, cursor);
                if (cursor >= tokens.size() || !SliccSourceTokens.identifier(tokens.get(cursor))) continue;
                SliccSourceTokens.Token name = tokens.get(cursor++);
                StringBuilder qualified = new StringBuilder(name.text());
                while (at(tokens, cursor, "::") && cursor + 1 < tokens.size() && SliccSourceTokens.identifier(tokens.get(cursor + 1))) {
                    name = tokens.get(cursor + 1);
                    qualified.append("::").append(name.text());
                    cursor += 2;
                }
                if (at(tokens, cursor, "<")) cursor = balanced(tokens, cursor, "<", ">") + 1;
                cursor = skipAttributes(tokens, cursor);
                if (at(tokens, cursor, "final")) cursor = skipAttributes(tokens, cursor + 1);
                if (at(tokens, cursor, ":")) cursor = baseClauseEnd(tokens, cursor + 1);
                if (at(tokens, cursor, "{")) {
                    definitions.add(new Definition(qualify(scopes, qualified.toString()), name.offset(), name.text().length()));
                    scopes.addLast(new Scope(qualified.toString(), true));
                    index = cursor;
                    continue;
                }
            }
            if ((text.equals("using") || text.equals("typedef")) && allowed(scopes)) {
                int end = index + 1;
                while (end < tokens.size() && !at(tokens, end, ";") && !at(tokens, end, "{")) end++;
                if (at(tokens, end, ";")) {
                    SliccSourceTokens.Token name = null;
                    if (text.equals("using") && index + 2 < end && at(tokens, index + 2, "=")
                            && SliccSourceTokens.identifier(tokens.get(index + 1))) {
                        name = tokens.get(index + 1);
                    } else if (text.equals("typedef")) {
                        name = typedefName(tokens, index + 1, end);
                    }
                    if (name != null) definitions.add(new Definition(qualify(scopes, name.text()), name.offset(), name.text().length()));
                    index = end;
                    continue;
                }
            }
            if (text.equals("{")) scopes.addLast(new Scope("", false));
            else if (text.equals("}") && !scopes.isEmpty()) scopes.removeLast();
        }
        return definitions;
    }

    private static boolean allowed(Deque<Scope> scopes) {
        return scopes.isEmpty() || scopes.getLast().types;
    }

    private static int skipAttributes(List<SliccSourceTokens.Token> tokens, int start) {
        int cursor = start;
        while (at(tokens, cursor, "[") && at(tokens, cursor + 1, "[")) {
            cursor = balanced(tokens, cursor, "[", "]") + 1;
        }
        return cursor;
    }

    private static int baseClauseEnd(List<SliccSourceTokens.Token> tokens, int start) {
        int cursor = start;
        while (cursor < tokens.size() && !at(tokens, cursor, "{")) {
            ProgressManager.checkCanceled();
            if (tokens.get(cursor).literal() || Set.of(";", "}", "(", ")", "*", "&", "=", "[", "]").contains(tokens.get(cursor).text())) return -1;
            cursor++;
        }
        return cursor;
    }

    private static SliccSourceTokens.Token typedefName(List<SliccSourceTokens.Token> tokens, int start, int end) {
        for (int cursor = start; cursor < end; cursor++) {
            if (at(tokens, cursor, "<")) {
                cursor = balanced(tokens, cursor, "<", ">");
            } else if (Set.of("(", ")", "[", "]", ",", "=", "}").contains(tokens.get(cursor).text()) || tokens.get(cursor).literal()) {
                return null;
            }
        }
        return end - 1 > start && SliccSourceTokens.identifier(tokens.get(end - 1)) ? tokens.get(end - 1) : null;
    }

    private static String qualify(Deque<Scope> scopes, String name) {
        StringJoiner prefix = new StringJoiner("::");
        for (Scope scope : scopes) if (!scope.name.isEmpty()) prefix.add(scope.name);
        return prefix.length() == 0 ? name : prefix + "::" + name;
    }

    private static int balanced(List<SliccSourceTokens.Token> tokens, int start, String opening, String closing) {
        int depth = 0;
        for (int index = start; index < tokens.size(); index++) {
            if (at(tokens, index, opening)) depth++;
            if (at(tokens, index, closing) && --depth == 0) return index;
        }
        return tokens.size() - 1;
    }

    private static boolean at(List<SliccSourceTokens.Token> tokens, int index, String value) {
        return index >= 0 && index < tokens.size() && !tokens.get(index).literal() && value.equals(tokens.get(index).text());
    }
}
