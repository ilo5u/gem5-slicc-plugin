package com.slicc.plugin.navigation;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.ProcessingContext;
import com.slicc.plugin.lang.SliccLanguage;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.psi.SliccNamedElement;
import org.jetbrains.annotations.NotNull;

/**
 * Attaches a {@link SliccReference} to every bare IDENTIFIER leaf in SLICC files
 * that is NOT itself the name-defining position of a declaration.
 *
 * This makes Ctrl+Click / Go-to-Declaration work on any identifier usage
 * (action call inside a transition body, type name in a field, etc.).
 */
public class SliccReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(LeafPsiElement.class)
                        .withElementType(SliccTokenTypes.IDENTIFIER)
                        .withLanguage(SliccLanguage.INSTANCE),
                new PsiReferenceProvider() {
                    @NotNull
                    @Override
                    public PsiReference[] getReferencesByElement(
                            @NotNull PsiElement element,
                            @NotNull ProcessingContext context) {

                        // Don't attach a reference to the defining name itself
                        if (element.getParent() instanceof SliccNamedElement named
                                && element.equals(named.getNameIdentifier())) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        return new PsiReference[]{new SliccReference(element)};
                    }
                });
    }
}
