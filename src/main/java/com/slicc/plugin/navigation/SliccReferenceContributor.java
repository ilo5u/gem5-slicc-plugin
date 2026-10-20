package com.slicc.plugin.navigation;

import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.slicc.plugin.lang.SliccLanguage;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SliccReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {

        // Match any PSI element with IDENTIFIER token type in the SLICC language.
        // Avoid restricting to LeafPsiElement.class — the exact class varies by platform version.
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement()
                        .withElementType(SliccTokenTypes.IDENTIFIER),
                new PsiReferenceProvider() {
                    @NotNull
                    @Override
                    public PsiReference[] getReferencesByElement(
                            @NotNull PsiElement element,
                            @NotNull ProcessingContext context) {

                        // Manual language check
                        if (!element.getLanguage().isKindOf(SliccLanguage.INSTANCE)) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        // On the declaration name: self-reference
                        if (element.getParent() instanceof SliccNamedElement named
                                && element.equals(named.getNameIdentifier())) {
                            return new PsiReference[]{new PsiReferenceBase<>(element,
                                    new TextRange(0, element.getTextLength())) {
                                @Override
                                public @Nullable PsiElement resolve() { return named; }
                            }};
                        }

                        // Skip REFERENCE nodes (function name markers) —
                        // but if the grandparent is a named element and this is its
                        // name identifier, return a self-reference so Find Usages works.
                        if (element.getParent() instanceof SliccCompositeElement parent
                                && parent.getNode().getElementType() == SliccTypes.REFERENCE) {
                            PsiElement grandParent = parent.getParent();
                            if (grandParent instanceof SliccFunctionDecl function
                                    && element.equals(function.getNameIdentifier())) {
                                return new PsiReference[]{new SliccReference(element)};
                            }
                            if (grandParent instanceof SliccNamedElement named
                                    && element.equals(named.getNameIdentifier())) {
                                return new PsiReference[]{new PsiReferenceBase<>(element,
                                        new TextRange(0, element.getTextLength())) {
                                    @Override
                                    public @Nullable PsiElement resolve() { return named; }
                                }};
                            }
                            return PsiReference.EMPTY_ARRAY;
                        }

                        return new PsiReference[]{new SliccReference(element)};
                    }
                });
    }
}
