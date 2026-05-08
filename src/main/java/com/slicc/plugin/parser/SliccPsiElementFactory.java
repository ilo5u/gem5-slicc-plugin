package com.slicc.plugin.parser;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.slicc.plugin.psi.*;

public class SliccPsiElementFactory {

    public static PsiElement createElement(ASTNode node) {
        if (node.getElementType() == SliccTypes.MACHINE_DECL)     return new SliccMachineDecl(node);
        if (node.getElementType() == SliccTypes.STRUCTURE_DECL)   return new SliccStructureDecl(node);
        if (node.getElementType() == SliccTypes.ENUMERATION_DECL) return new SliccEnumerationDecl(node);
        if (node.getElementType() == SliccTypes.STATE_DECL)       return new SliccStateDecl(node);
        if (node.getElementType() == SliccTypes.EXTERNAL_TYPE_DECL) return new SliccExternalTypeDecl(node);
        if (node.getElementType() == SliccTypes.ACTION_DECL)      return new SliccActionDecl(node);
        if (node.getElementType() == SliccTypes.TRANSITION_DECL)  return new SliccTransitionDecl(node);
        if (node.getElementType() == SliccTypes.FUNCTION_DECL)    return new SliccFunctionDecl(node);
        if (node.getElementType() == SliccTypes.IN_PORT_DECL)     return new SliccInPortDecl(node);
        if (node.getElementType() == SliccTypes.OUT_PORT_DECL)    return new SliccOutPortDecl(node);
        if (node.getElementType() == SliccTypes.FIELD_DECL)       return new SliccFieldDecl(node);
        if (node.getElementType() == SliccTypes.ENUM_MEMBER)      return new SliccEnumMember(node);
        if (node.getElementType() == SliccTypes.INCLUDE_DECL)     return new SliccIncludeDecl(node);
        return new SliccCompositeElement(node);
    }
}
