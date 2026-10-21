package com.slicc.plugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.slicc.plugin.lexer.SliccTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PSI node for  include "path/to/file.sm";
 *
 * Provides a file reference on the string literal so that Ctrl+Click
 * (or Ctrl+B) on the include path navigates to the included file.
 */
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

    /**
     * Provide a reference on the whole INCLUDE_DECL node so Ctrl+Click
     * anywhere on  include "file.sm"  navigates to the file.
     */
    @Override
    @Nullable
    public PsiReference getReference() {
        ASTNode strNode = getNode().findChildByType(SliccTokenTypes.STRING_LITERAL);
        if (strNode == null) return null;
        String path = getIncludedPath();
        if (path == null || path.isBlank()) return null;

        // Offset of the string literal within this element, +1 to skip opening quote
        int startOffset = strNode.getStartOffsetInParent() + 1;
        int endOffset   = startOffset + path.length();

        return new PsiReferenceBase<PsiElement>(this, new TextRange(startOffset, endOffset)) {
            @Override
            @Nullable
            public PsiElement resolve() {
                VirtualFile vf = getElement().getContainingFile().getVirtualFile();
                if (vf == null) return null;
                VirtualFile dir = vf.getParent();
                if (dir == null) return null;
                VirtualFile target = dir.findFileByRelativePath(path);
                if (target == null) return null;
                return PsiManager.getInstance(getElement().getProject()).findFile(target);
            }

            @Override
            public boolean isSoft() {
                return false; // unresolved → show error highlight
            }
        };
    }
}
