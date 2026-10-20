package com.slicc.plugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.slicc.plugin.icons.SliccIcons;
import com.slicc.plugin.lexer.SliccTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public final class SliccFunctionDecl extends SliccNamedElement {
    public SliccFunctionDecl(@NotNull ASTNode node) { super(node); }
    @Override protected Icon getIcon() { return SliccIcons.FUNCTION; }

    public boolean isDefinition() {
        return getNode().findChildByType(SliccTypes.BLOCK) != null;
    }

    @NotNull
    public List<String> getParameterTypes() {
        List<String> types = new ArrayList<>();
        ASTNode parameters = getNode().findChildByType(SliccTypes.PARAM_LIST);
        if (parameters == null) return types;
        for (ASTNode parameter = parameters.getFirstChildNode(); parameter != null; parameter = parameter.getTreeNext()) {
            if (parameter.getElementType() != SliccTypes.FIELD_DECL) continue;
            ASTNode type = parameter.findChildByType(SliccTypes.TYPE_REF);
            types.add(SliccPsiUtil.typeText(type));
        }
        return types;
    }

    @Override
    public ItemPresentation getPresentation() {
        String signature = getName() + "(" + String.join(", ", getParameterTypes()) + ")";
        String location = SliccPsiUtil.declarationLocation(this);
        return new ItemPresentation() {
            @Override public String getPresentableText() { return signature; }
            @Override public String getLocationString() { return location; }
            @Override public Icon getIcon(boolean unused) { return SliccIcons.FUNCTION; }
        };
    }

    /**
     * The function name is the IDENTIFIER inside the REFERENCE child node
     * (placed there by parseFunctionDecl to distinguish it from the return type).
     */
    @Override
    @Nullable
    public PsiElement getNameIdentifier() {
        // First look for a REFERENCE child (the name marker we added in the parser)
        ASTNode refNode = getNode().findChildByType(SliccTypes.REFERENCE);
        if (refNode != null) {
            ASTNode id = refNode.findChildByType(SliccTokenTypes.IDENTIFIER);
            if (id != null) return id.getPsi();
        }
        // Fallback: skip the first TYPE_REF child and find the next IDENTIFIER
        boolean seenTypeRef = false;
        for (ASTNode child = getNode().getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getElementType() == SliccTypes.TYPE_REF) {
                seenTypeRef = true;
                continue;
            }
            if (seenTypeRef && child.getElementType() == SliccTokenTypes.IDENTIFIER) {
                return child.getPsi();
            }
        }
        return null;
    }
}
