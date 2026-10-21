package com.slicc.plugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.lexer.SliccTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SliccPsiUtil {

    public static String typeText(ASTNode type) {
        StringBuilder text = new StringBuilder();
        if (type != null) {
            for (ASTNode token = type.getFirstChildNode(); token != null; token = token.getTreeNext()) {
                if (!isTrivia(token)) text.append(token.getText());
            }
        }
        return text.toString();
    }

    @Nullable
    public static String withoutReference(@Nullable String type) {
        if (type == null) return null;
        int end = type.length();
        while (end > 0 && type.charAt(end - 1) == '&') end--;
        return type.substring(0, end);
    }

    @Nullable
    public static String baseTypeName(@Nullable String type) {
        if (type == null) return null;
        int end = type.length();
        while (end > 0 && (type.charAt(end - 1) == '*' || type.charAt(end - 1) == '&')) end--;
        return type.substring(0, end);
    }

    public static String declarationLocation(PsiElement declaration) {
        SliccNamedElement owner = PsiTreeUtil.getParentOfType(declaration,
                SliccMachineDecl.class, SliccStructureDecl.class, SliccExternalTypeDecl.class);
        String file = declaration.getContainingFile().getName();
        return owner == null ? file : owner.getName() + " — " + file;
    }

    private SliccPsiUtil() {}

    @Nullable
    public static ASTNode previousSignificantSibling(@NotNull ASTNode node) {
        ASTNode previous = node.getTreePrev();
        while (previous != null && isTrivia(previous)) previous = previous.getTreePrev();
        return previous;
    }

    @Nullable
    public static ASTNode nextSignificantSibling(@NotNull ASTNode node) {
        ASTNode next = node.getTreeNext();
        while (next != null && isTrivia(next)) next = next.getTreeNext();
        return next;
    }

    public static boolean isTrivia(@NotNull ASTNode node) {
        return node.getElementType() == TokenType.WHITE_SPACE
                || node.getElementType() == SliccTokenTypes.LINE_COMMENT
                || node.getElementType() == SliccTokenTypes.BLOCK_COMMENT;
    }
}
