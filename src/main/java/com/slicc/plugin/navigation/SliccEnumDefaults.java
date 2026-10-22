package com.slicc.plugin.navigation;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SliccEnumDefaults {
    private static final Resolution UNHANDLED = new Resolution(false, false, null);
    private static final Resolution DEFERRED = new Resolution(true, true, null);

    private SliccEnumDefaults() {}

    public record Resolution(boolean handled, boolean deferred, @Nullable SliccEnumMember member) {}

    @NotNull
    public static Resolution resolve(@NotNull PsiElement value) {
        PsiElement owner = defaultOwner(value);
        if (owner == null) return UNHANDLED;
        if (DumbService.isDumb(value.getProject())) return DEFERRED;
        SliccFunctionNavigation.Scope scope = new SliccFunctionNavigation.Scope(value);
        if (scope.hasAmbiguousOwners()) return DEFERRED;
        for (SliccFieldDecl variable : SliccGotoDeclarationHandler.collectVisibleVariables(value)) {
            if (value.getText().equals(variable.getName())) return UNHANDLED;
        }
        SliccNamedElement enumeration;
        if (owner instanceof SliccEnumerationDecl || owner instanceof SliccStateDecl) {
            enumeration = (SliccNamedElement) owner;
        } else if (owner instanceof SliccFieldDecl field) {
            ASTNode type = field.getNode().findChildByType(SliccTypes.TYPE_REF);
            String name = SliccPsiUtil.baseTypeName(SliccPsiUtil.typeText(type));
            SliccNamedElement selected = null;
            int bestRank = Integer.MAX_VALUE;
            boolean ambiguous = false;
            for (PsiFile file : scope.files) {
                for (SliccNamedElement candidate : SliccFileSymbols.named(file, name)) {
                    if (!(candidate instanceof SliccEnumerationDecl || candidate instanceof SliccStateDecl
                            || candidate instanceof SliccStructureDecl || candidate instanceof SliccExternalTypeDecl)) continue;
                    ProgressManager.checkCanceled();
                    int rank = scope.rank(candidate);
                    if (rank < 0 || rank > bestRank) continue;
                    if (rank == bestRank && selected != null) {
                        ambiguous = true;
                    } else {
                        selected = candidate;
                        bestRank = rank;
                        ambiguous = false;
                    }
                }
            }
            if (ambiguous) return DEFERRED;
            if (!(selected instanceof SliccEnumerationDecl || selected instanceof SliccStateDecl)) return UNHANDLED;
            enumeration = selected;
        } else {
            return UNHANDLED;
        }
        if (PsiTreeUtil.findChildOfType(enumeration, PsiErrorElement.class) != null
                || enumeration.getNode().findChildByType(SliccTokenTypes.OP_RBRACE) == null) return DEFERRED;
        String name = value.getText();
        SliccEnumMember selected = null;
        for (PsiElement child : enumeration.getChildren()) {
            if (child instanceof SliccEnumMember member && name.equals(member.getName())) {
                if (selected != null) return DEFERRED;
                selected = member;
            }
        }
        if (selected != null) return new Resolution(true, false, selected);
        for (PsiElement child : enumeration.getChildren()) {
            if (child instanceof SliccEnumMember member && isAlias(value, member)) {
                if (selected != null) return DEFERRED;
                selected = member;
            }
        }
        return new Resolution(true, false, selected);
    }

    public static boolean isAlias(@NotNull PsiElement value, @NotNull SliccEnumMember member) {
        PsiElement owner = member.getParent();
        return owner instanceof SliccNamedElement named && named.getName() != null && member.getName() != null
                && value.getText().equals(named.getName() + "_" + member.getName()) && defaultOwner(value) != null;
    }

    @Nullable
    private static PsiElement defaultOwner(PsiElement value) {
        if (value.getNode().getElementType() != SliccTokenTypes.IDENTIFIER) return null;
        PsiElement attribute = value.getParent();
        if (attribute == null || attribute.getNode().getElementType() != SliccTypes.ATTRIBUTE) return null;
        ASTNode key = attribute.getNode().getFirstChildNode();
        while (key != null && SliccPsiUtil.isTrivia(key)) key = key.getTreeNext();
        if (key == null || key.getElementType() != SliccTokenTypes.KW_DEFAULT) return null;
        ASTNode equals = SliccPsiUtil.nextSignificantSibling(key);
        if (equals == null || equals.getElementType() != SliccTokenTypes.OP_EQUALS) return null;
        ASTNode token = SliccPsiUtil.nextSignificantSibling(equals);
        if (token != value.getNode() || SliccPsiUtil.nextSignificantSibling(token) != null) return null;
        PsiElement list = attribute.getParent();
        return list != null && list.getNode().getElementType() == SliccTypes.ATTRIBUTE_LIST ? list.getParent() : null;
    }
}
