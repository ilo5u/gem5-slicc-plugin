package com.slicc.plugin.navigation;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.ProcessingContext;
import com.slicc.plugin.lang.SliccLanguage;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Attaches {@link SliccReference} to IDENTIFIER leaves that are NOT:
 *   - declaration name positions
 *   - inside TYPE_REF nodes (those get references via SliccCompositeElement.getReference())
 *   - inside REFERENCE nodes (function name markers)
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

                        String text = element.getText();

                        // Skip declaration name positions
                        if (element.getParent() instanceof SliccNamedElement named
                                && element.equals(named.getNameIdentifier())) {
                            System.err.println("[SLICC-REF] skip decl name: '" + text + "'");
                            return PsiReference.EMPTY_ARRAY;
                        }
                        // Skip identifiers inside TYPE_REF — handled by SliccCompositeElement.getReference()
                        if (element.getParent() instanceof SliccCompositeElement parent
                                && parent.getNode().getElementType() == SliccTypes.TYPE_REF) {
                            System.err.println("[SLICC-REF] skip TYPE_REF child: '" + text + "'");
                            return PsiReference.EMPTY_ARRAY;
                        }
                        // Skip identifiers inside REFERENCE nodes (function name markers)
                        if (element.getParent() instanceof SliccCompositeElement parent
                                && parent.getNode().getElementType() == SliccTypes.REFERENCE) {
                            System.err.println("[SLICC-REF] skip REFERENCE child: '" + text + "'");
                            return PsiReference.EMPTY_ARRAY;
                        }

                        System.err.println("[SLICC-REF] attach ref to IDENTIFIER: '" + text + "'"
                                + " parent=" + element.getParent().getClass().getSimpleName());
                        return new PsiReference[]{new SliccReference(element)};
                    }
                });
    }
}