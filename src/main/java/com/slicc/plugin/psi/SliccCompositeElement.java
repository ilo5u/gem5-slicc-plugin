package com.slicc.plugin.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class SliccCompositeElement extends ASTWrapperPsiElement {
    public SliccCompositeElement(@NotNull ASTNode node) {
        super(node);
    }
}