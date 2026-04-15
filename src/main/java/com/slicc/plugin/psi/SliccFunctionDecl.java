package com.slicc.plugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.slicc.plugin.icons.SliccIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import javax.swing.*;

public final class SliccFunctionDecl extends SliccNamedElement {
    public SliccFunctionDecl(@NotNull ASTNode node) { super(node); }
    @Override protected Icon getIcon() { return SliccIcons.FUNCTION; }
}
