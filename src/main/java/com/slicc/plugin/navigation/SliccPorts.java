package com.slicc.plugin.navigation;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.slicc.plugin.psi.SliccInPortDecl;
import com.slicc.plugin.psi.SliccNamedElement;
import com.slicc.plugin.psi.SliccOutPortDecl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SliccPorts {
    private SliccPorts() {}

    public record Binding(boolean knownName, @Nullable SliccNamedElement declaration) {
        @Nullable
        public String type() {
            return typeOf(declaration);
        }
    }

    @Nullable
    public static String typeOf(@Nullable SliccNamedElement declaration) {
        return declaration instanceof SliccInPortDecl ? "InPort"
                : declaration instanceof SliccOutPortDecl ? "OutPort" : null;
    }

    @NotNull
    public static Binding resolve(String name, PsiElement position) {
        return resolve(name, position, null);
    }

    @NotNull
    public static Binding resolve(String name, PsiElement position, @Nullable PsiFile originalFile) {
        PsiElement context = originalContext(position, originalFile);
        if (DumbService.isDumb(context.getProject())) return new Binding(false, null);
        SliccFunctionNavigation.Scope scope = new SliccFunctionNavigation.Scope(context);
        return resolve(name, scope);
    }

    @NotNull
    public static List<SliccNamedElement> visible(PsiElement position, @Nullable PsiFile originalFile) {
        PsiElement context = originalContext(position, originalFile);
        if (DumbService.isDumb(context.getProject())) return List.of();
        SliccFunctionNavigation.Scope scope = new SliccFunctionNavigation.Scope(context);
        if (scope.hasAmbiguousOwners()) return List.of();
        Set<String> names = new LinkedHashSet<>();
        for (PsiFile file : scope.files) {
            List<SliccNamedElement> declarations = new ArrayList<>(SliccFileSymbols.ofType(file, SliccInPortDecl.class));
            declarations.addAll(SliccFileSymbols.ofType(file, SliccOutPortDecl.class));
            for (SliccNamedElement declaration : declarations) {
                ProgressManager.checkCanceled();
                if (declaration.getName() != null) names.add(declaration.getName());
            }
        }
        List<SliccNamedElement> result = new ArrayList<>();
        for (String name : names) {
            Binding binding = resolve(name, scope);
            if (binding.declaration() != null) result.add(binding.declaration());
        }
        return result;
    }

    private static Binding resolve(String name, SliccFunctionNavigation.Scope scope) {
        SliccNamedElement selected = null;
        int bestRank = Integer.MAX_VALUE;
        boolean knownName = false;
        boolean ambiguous = scope.hasAmbiguousOwners();
        for (PsiFile file : scope.files) {
            for (SliccNamedElement candidate : SliccFileSymbols.named(file, name)) {
                if (!(candidate instanceof SliccInPortDecl || candidate instanceof SliccOutPortDecl)) continue;
                ProgressManager.checkCanceled();
                int rank = scope.rank(candidate);
                if (rank < 0 || rank > bestRank) continue;
                knownName = true;
                if (scope.hasAmbiguousOwners()) continue;
                if (rank < bestRank) {
                    selected = candidate;
                    bestRank = rank;
                    ambiguous = false;
                } else if (candidate != selected) {
                    ambiguous = true;
                }
            }
        }
        return new Binding(knownName, ambiguous ? null : selected);
    }

    private static PsiElement originalContext(PsiElement position, @Nullable PsiFile originalFile) {
        if (originalFile != null && originalFile != position.getContainingFile() && originalFile.getTextLength() > 0) {
            PsiElement original = originalFile.findElementAt(Math.min(position.getTextOffset(), originalFile.getTextLength() - 1));
            if (original != null) return original;
        }
        return position;
    }
}
