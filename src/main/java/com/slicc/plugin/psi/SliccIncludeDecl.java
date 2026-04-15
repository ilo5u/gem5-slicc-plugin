package com.slicc.plugin.psi;

import com.intellij.lang.ASTNode;
import com.slicc.plugin.lexer.SliccTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SliccIncludeDecl extends SliccCompositeElement {

    public SliccIncludeDecl(@NotNull ASTNode node) { super(node); }

    /** Returns the unquoted path from  include "path/to/file.sm"; */
    @Nullable
    public String getIncludedPath() {
        ASTNode str = getNode().findChildByType(SliccTokenTypes.STRING_LITERAL);
        if (str == null) return null;
        String text = str.getText();
        return (text.length() >= 2) ? text.substring(1, text.length() - 1) : null;
    }
}
