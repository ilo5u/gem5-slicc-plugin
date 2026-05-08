package com.slicc.plugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.slicc.plugin.icons.SliccIcons;
import com.slicc.plugin.lexer.SliccTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class SliccInPortDecl extends SliccNamedElement {

    public SliccInPortDecl(@NotNull ASTNode node) { super(node); }

    @Override protected Icon getIcon() { return SliccIcons.FUNCTION; }

    /** in_port(portName, Type, ...) — name is the first IDENTIFIER after '(' */
    @Nullable
    @Override
    public PsiElement getNameIdentifier() {
        ASTNode lparen = getNode().findChildByType(SliccTokenTypes.OP_LPAREN);
        if (lparen != null) {
            for (ASTNode cur = lparen.getTreeNext(); cur != null; cur = cur.getTreeNext()) {
                if (cur.getElementType() == SliccTokenTypes.IDENTIFIER) return cur.getPsi();
            }
        }
        return null;
    }

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

