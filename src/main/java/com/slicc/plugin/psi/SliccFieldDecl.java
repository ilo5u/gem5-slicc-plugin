package com.slicc.plugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.slicc.plugin.icons.SliccIcons;
import com.slicc.plugin.lexer.SliccTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * PSI node for a field/variable declaration such as:
 *   State state, desc = "";
 *   Addr address, desc = "for debugging purpose";
 *   bool v, default = "true", desc = "...";
 *
 * The PSI structure produced by parseFieldOrVar is:
 *   FIELD_DECL
 *     TYPE_REF         ← type name (e.g. "State", "Addr", "bool")
 *     IDENTIFIER       ← variable name (e.g. "state", "address", "v")
 *     ATTRIBUTE_LIST   ← optional , key = value pairs
 *
 * getNameIdentifier() must skip the TYPE_REF and find the IDENTIFIER that
 * follows it as the next direct sibling.
 */
public final class SliccFieldDecl extends SliccNamedElement {
    public SliccFieldDecl(@NotNull ASTNode node) { super(node); }
    @Override protected Icon getIcon() { return SliccIcons.FILE; }

    @Override
    @Nullable
    public PsiElement getNameIdentifier() {
        boolean seenTypeRef = false;
        for (ASTNode child = getNode().getFirstChildNode();
             child != null;
             child = child.getTreeNext()) {
            if (child.getElementType() == SliccTypes.TYPE_REF) {
                seenTypeRef = true;
                continue;
            }
            // The IDENTIFIER right after TYPE_REF is the field name
            if (seenTypeRef && child.getElementType() == SliccTokenTypes.IDENTIFIER) {
                return child.getPsi();
            }
        }
        return null;
    }
}