package com.slicc.plugin.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import com.slicc.plugin.navigation.SliccReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SliccCompositeElement extends ASTWrapperPsiElement {

    public SliccCompositeElement(@NotNull ASTNode node) {
        super(node);
    }

    /**
     * Provide a reference directly on TYPE_REF nodes so that Ctrl+Click on
     * a type name (e.g. TBETable, RegionList, State) resolves to its declaration.
     *
     * This approach bypasses PsiReferenceContributor pattern matching entirely,
     * which can fail for custom PSI element classes.
     */
    @Override
    @Nullable
    public PsiReference getReference() {
        if (getNode().getElementType() == SliccTypes.TYPE_REF) {
            String text = getText();
            System.err.println("[SLICC-TYPEREF] getReference() called on TYPE_REF, text='"
                    + text + "' parent=" + getParent().getClass().getSimpleName());
            if (text != null && !text.isBlank() && !text.equals("void")) {
                return new SliccReference(this);
            }
        }
        return null;
    }
}