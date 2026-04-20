package com.slicc.plugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.slicc.plugin.icons.SliccIcons;
import com.slicc.plugin.lexer.SliccTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class SliccExternalTypeDecl extends SliccNamedElement {
    public SliccExternalTypeDecl(@NotNull ASTNode node) { super(node); }
    @Override protected Icon getIcon() { return SliccIcons.STRUCTURE; }

    /**
     * The type name lives inside the REFERENCE child node (added by parseExternalTypeDecl).
     * It may be an IDENTIFIER (CacheMemory, std) or a keyword token (int, bool, string, void).
     * For scoped names like std::string we return the first token (std) as the representative
     * identifier; the full text is available via getName() which walks the whole REFERENCE node.
     */
    @Override
    @Nullable
    public PsiElement getNameIdentifier() {
        ASTNode refNode = getNode().findChildByType(SliccTypes.REFERENCE);
        if (refNode != null) {
            // Return the first leaf — may be IDENTIFIER or a keyword token
            ASTNode first = refNode.getFirstChildNode();
            if (first != null) return first.getPsi();
        }
        return null;
    }

    /** Full type name text, including :: (e.g. "std::string", "int", "CacheMemory"). */
    @Override
    @Nullable
    public String getName() {
        ASTNode refNode = getNode().findChildByType(SliccTypes.REFERENCE);
        return refNode != null ? refNode.getText() : null;
    }
}