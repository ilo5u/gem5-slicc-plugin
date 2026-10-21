package com.slicc.plugin.navigation;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.psi.SliccNamedElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SliccFileSymbols {

    private static final Key<CachedValue<Symbols>> CACHE = Key.create("SLICC_FILE_SYMBOLS");

    private SliccFileSymbols() {}

    @NotNull
    public static List<SliccNamedElement> named(@NotNull PsiFile file, @NotNull String name) {
        return symbols(file).byName.getOrDefault(name, List.of());
    }

    @NotNull
    public static <Declaration extends SliccNamedElement> List<Declaration> named(
            @NotNull PsiFile file, @NotNull String name, @NotNull Class<Declaration> declarationClass) {
        List<Declaration> result = new ArrayList<>();
        for (SliccNamedElement declaration : named(file, name)) {
            if (declarationClass.isInstance(declaration)) result.add(declarationClass.cast(declaration));
        }
        return result;
    }

    @NotNull
    @SuppressWarnings("unchecked")
    public static <Declaration extends SliccNamedElement> List<Declaration> ofType(
            @NotNull PsiFile file, @NotNull Class<Declaration> declarationClass) {
        Symbols symbols = symbols(file);
        if (declarationClass == SliccNamedElement.class) return (List<Declaration>) symbols.all;
        return (List<Declaration>) symbols.byType.getOrDefault(declarationClass, List.of());
    }

    private static Symbols symbols(PsiFile file) {
        return CachedValuesManager.getCachedValue(file, CACHE,
                () -> CachedValueProvider.Result.create(new Symbols(file), file));
    }

    private static final class Symbols {
        private final List<SliccNamedElement> all;
        private final Map<String, List<SliccNamedElement>> byName = new HashMap<>();
        private final Map<Class<?>, List<SliccNamedElement>> byType = new HashMap<>();

        private Symbols(PsiFile file) {
            all = List.copyOf(PsiTreeUtil.collectElementsOfType(file, SliccNamedElement.class));
            for (SliccNamedElement declaration : all) {
                ProgressManager.checkCanceled();
                String name = declaration.getName();
                if (name != null) byName.computeIfAbsent(name, unused -> new ArrayList<>()).add(declaration);
                byType.computeIfAbsent(declaration.getClass(), unused -> new ArrayList<>()).add(declaration);
            }
            byName.replaceAll((name, declarations) -> List.copyOf(declarations));
            byType.replaceAll((declarationClass, declarations) -> List.copyOf(declarations));
        }
    }
}
