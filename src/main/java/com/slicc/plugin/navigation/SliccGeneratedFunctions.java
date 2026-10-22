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

import java.util.ArrayList;
import java.util.List;

public final class SliccGeneratedFunctions {
    private SliccGeneratedFunctions() {}

    @Nullable
    public static SliccNamedElement resolve(@NotNull PsiElement call) {
        String name = call.getText();
        boolean permission = name.endsWith("_to_permission");
        if (!permission && !name.endsWith("_to_string")) return null;
        ASTNode node = call.getNode();
        if (node == null || node.getElementType() != SliccTokenTypes.IDENTIFIER
                || DumbService.isDumb(call.getProject())) return null;
        ASTNode next = SliccPsiUtil.nextSignificantSibling(node);
        if (next == null || next.getElementType() != SliccTokenTypes.OP_LPAREN) return null;
        if (call.getParent().getNode().getElementType() == SliccTypes.TYPE_REF) return null;
        SliccFunctionDecl declaration = PsiTreeUtil.getParentOfType(call, SliccFunctionDecl.class);
        if (declaration != null && call.equals(declaration.getNameIdentifier())) return null;
        ASTNode previous = SliccPsiUtil.previousSignificantSibling(node);
        if (previous != null && (previous.getElementType() == SliccTokenTypes.OP_DOT
                || previous.getElementType() == SliccTokenTypes.OP_SCOPE
                || previous.getElementType() == SliccTokenTypes.OP_COLON
                || previous.getElementType() == SliccTokenTypes.KW_NEW)) return null;
        SliccFunctionNavigation.Scope scope = new SliccFunctionNavigation.Scope(call);
        if (scope.hasAmbiguousOwners()) return null;
        for (SliccFieldDecl variable : SliccGotoDeclarationHandler.collectVisibleVariables(call)) {
            if (name.equals(variable.getName())) return null;
        }
        SliccNamedElement selected = null;
        int bestRank = Integer.MAX_VALUE;
        boolean ambiguous = false;
        for (PsiFile file : scope.files) {
            List<SliccNamedElement> types = new ArrayList<>(SliccFileSymbols.ofType(file, SliccStateDecl.class));
            if (!permission) types.addAll(SliccFileSymbols.ofType(file, SliccEnumerationDecl.class));
            for (SliccNamedElement candidate : types) {
                ProgressManager.checkCanceled();
                int rank = scope.rank(candidate);
                if (rank < 0 || rank > bestRank) continue;
                String type = scope.generatedTypeName(candidate);
                if (type == null || !name.equals(type + (permission ? "_to_permission" : "_to_string"))) continue;
                if (rank < bestRank) {
                    selected = candidate;
                    bestRank = rank;
                    ambiguous = candidate.getNode().findChildByType(SliccTokenTypes.OP_RBRACE) == null
                            || PsiTreeUtil.findChildOfType(candidate, PsiErrorElement.class) != null;
                } else if (candidate != selected) {
                    ambiguous = true;
                }
            }
        }
        return ambiguous ? null : selected;
    }
}
