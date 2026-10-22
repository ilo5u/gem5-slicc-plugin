package com.slicc.plugin.lexer;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SliccBuiltinFunctions {
    private static final List<String> NAMES = List.of("DPRINTF", "DPRINTFN", "APPEND_TRANSITION_COMMENT", "stallPort");
    private static final Map<String, IElementType> TOKENS = Map.of(
            "DPRINTF", SliccTokenTypes.KW_DPRINTF,
            "DPRINTFN", SliccTokenTypes.KW_DPRINTFN,
            "APPEND_TRANSITION_COMMENT", SliccTokenTypes.KW_APPEND_TRANSITION_COMMENT,
            "stallPort", SliccTokenTypes.KW_STALL_PORT);
    private static final Set<String> DEFAULT_NAMES = Set.of("error", "assert", "set_cache_entry",
            "unset_cache_entry", "set_tbe", "unset_tbe");

    private SliccBuiltinFunctions() {}

    @NotNull
    public static List<String> names() {
        return NAMES;
    }

    @Nullable
    public static IElementType tokenType(@NotNull String name) {
        return TOKENS.get(name);
    }

    public static boolean isToken(@Nullable IElementType type) {
        return type != null && TOKENS.containsValue(type);
    }

    public static boolean isDefaultName(@NotNull String name) {
        return TOKENS.containsKey(name) || DEFAULT_NAMES.contains(name);
    }
}
