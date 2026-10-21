package com.slicc.plugin.navigation;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.psi.SliccFieldDecl;
import com.slicc.plugin.psi.SliccFunctionDecl;
import com.slicc.plugin.psi.SliccPsiUtil;
import com.slicc.plugin.psi.SliccTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SliccMessageScope {

    private SliccMessageScope() {}

    public static boolean isMessageVariable(@NotNull String name) {
        return "in_msg".equals(name) || "out_msg".equals(name);
    }

    public static boolean isVisible(@NotNull String name, @NotNull PsiElement position) {
        return findParameter(name, position) != null || findScope(name, position) != null;
    }

    @Nullable
    public static SliccFieldDecl findParameter(@NotNull String name, @NotNull PsiElement position) {
        if (!isMessageVariable(name)) return null;
        SliccFunctionDecl function = PsiTreeUtil.getParentOfType(position, SliccFunctionDecl.class);
        if (function == null) return null;
        ASTNode parameters = function.getNode().findChildByType(SliccTypes.PARAM_LIST);
        if (parameters == null) return null;
        for (ASTNode parameter = parameters.getFirstChildNode(); parameter != null; parameter = parameter.getTreeNext()) {
            if (parameter.getPsi() instanceof SliccFieldDecl field && name.equals(field.getName())) return field;
        }
        return null;
    }

    @Nullable
    public static String resolveType(@NotNull String name, @NotNull PsiElement position) {
        PsiElement typeReference = findTypeReference(name, position);
        if (typeReference == null) return null;
        return typeReference.getNode().getElementType() == SliccTypes.TYPE_REF
                ? SliccPsiUtil.typeText(typeReference.getNode()) : typeReference.getText();
    }

    @Nullable
    public static PsiElement findTypeReference(@NotNull String name, @NotNull PsiElement position) {
        SliccFieldDecl parameter = findParameter(name, position);
        if (parameter != null) {
            ASTNode type = parameter.getNode().findChildByType(SliccTypes.TYPE_REF);
            return type != null ? type.getPsi() : null;
        }
        ASTNode scope = findScope(name, position);
        if (scope == null) return null;
        ASTNode argument = nextSignificant(scope.getFirstChildNode());
        if (argument == null || argument.getElementType() != SliccTokenTypes.OP_LPAREN) return null;
        int depth = 0;
        for (argument = nextSignificant(argument); argument != null;
             argument = nextSignificant(argument)) {
            IElementType token = argument.getElementType();
            if (token == SliccTypes.BLOCK) return null;
            if (depth == 0 && token == SliccTokenTypes.OP_COMMA) {
                ASTNode typeReference = nextSignificant(argument);
                return typeReference != null && typeReference.getElementType() == SliccTokenTypes.IDENTIFIER
                        ? typeReference.getPsi() : null;
            }
            if (token == SliccTokenTypes.OP_LPAREN || token == SliccTokenTypes.OP_LBRACKET
                    || token == SliccTokenTypes.OP_LBRACE) {
                depth++;
            } else if (token == SliccTokenTypes.OP_RPAREN || token == SliccTokenTypes.OP_RBRACKET
                    || token == SliccTokenTypes.OP_RBRACE) {
                if (depth == 0) return null;
                depth--;
            }
        }
        return null;
    }

    @Nullable
    private static ASTNode findScope(String name, PsiElement position) {
        if (!isMessageVariable(name)) return null;
        IElementType keyword = "in_msg".equals(name) ? SliccTokenTypes.KW_PEEK : SliccTokenTypes.KW_ENQUEUE;
        for (PsiElement current = position.getParent(); current != null && !(current instanceof PsiFile);
             current = current.getParent()) {
            if (current.getNode().getElementType() != SliccTypes.BLOCK) continue;
            PsiElement statement = current.getParent();
            if (statement == null || statement.getNode().getElementType() != SliccTypes.STATEMENT) continue;
            ASTNode first = statement.getNode().getFirstChildNode();
            if (first != null && first.getElementType() == keyword) return statement.getNode();
        }
        return null;
    }

    @Nullable
    private static ASTNode nextSignificant(@NotNull ASTNode node) {
        ASTNode next = node.getTreeNext();
        while (next != null && (next.getElementType() == TokenType.WHITE_SPACE
                || next.getElementType() == SliccTokenTypes.LINE_COMMENT
                || next.getElementType() == SliccTokenTypes.BLOCK_COMMENT)) {
            next = next.getTreeNext();
        }
        return next;
    }
}
