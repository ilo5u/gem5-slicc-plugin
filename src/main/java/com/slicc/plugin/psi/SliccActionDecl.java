package com.slicc.plugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.slicc.plugin.icons.SliccIcons;
import org.jetbrains.annotations.NotNull;
import javax.swing.*;

public final class SliccActionDecl extends SliccNamedElement {
    public SliccActionDecl(@NotNull ASTNode node) { super(node); }
    @Override protected Icon getIcon() { return SliccIcons.ACTION; }

    @Override
    public ItemPresentation getPresentation() {
        String name = "action " + getName();
        String location = SliccPsiUtil.declarationLocation(this);
        return new ItemPresentation() {
            @Override public String getPresentableText() { return name; }
            @Override public String getLocationString() { return location; }
            @Override public Icon getIcon(boolean unused) { return SliccIcons.ACTION; }
        };
    }
}
