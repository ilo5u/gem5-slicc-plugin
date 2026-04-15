package com.slicc.plugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.slicc.plugin.icons.SliccIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class SliccInPortDecl extends SliccNamedElement {

    public SliccInPortDecl(@NotNull ASTNode node) { super(node); }

    @Override protected Icon getIcon() { return SliccIcons.FUNCTION; }

    @Override
    public ItemPresentation getPresentation() {
        final String label = "in_port: " + getName();
        final Icon   icon  = getIcon();
        return new ItemPresentation() {
            @Override public @Nullable String getPresentableText()    { return label; }
            @Override public @Nullable String getLocationString()     { return null;  }
            @Override public @Nullable Icon   getIcon(boolean unused) { return icon;  }
        };
    }
}
