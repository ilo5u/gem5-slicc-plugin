package com.slicc.plugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.slicc.plugin.icons.SliccIcons;
import com.slicc.plugin.lexer.SliccTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import javax.swing.*;

public final class SliccMachineDecl extends SliccNamedElement {
    public SliccMachineDecl(@NotNull ASTNode node) { super(node); }
    @Override protected Icon getIcon() { return SliccIcons.MACHINE; }

    /**
     * machine(MachineType:SpPCache, ...) — the name is the identifier after ':', not the first one.
     * The first identifier (MachineType) is a type reference, not this declaration's name.
     */
    @Nullable
    @Override
    public PsiElement getNameIdentifier() {
        ASTNode colon = getNode().findChildByType(SliccTokenTypes.OP_COLON);
        if (colon != null) {
            ASTNode cur = colon.getTreeNext();
            while (cur != null) {
                if (cur.getElementType() == SliccTokenTypes.IDENTIFIER) return cur.getPsi();
                cur = cur.getTreeNext();
            }
        }
        ASTNode id = getNode().findChildByType(SliccTokenTypes.IDENTIFIER);
        return id != null ? id.getPsi() : null;
    }
}
