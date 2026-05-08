package com.slicc.plugin.highlighting;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SliccFoldingBuilder extends FoldingBuilderEx {

    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root,
                                                @NotNull Document document,
                                                boolean quick) {
        List<FoldingDescriptor> descriptors = new ArrayList<>();

        for (SliccMachineDecl m : PsiTreeUtil.collectElementsOfType(root, SliccMachineDecl.class)) {
            addBlockFold(m, document, descriptors);
        }
        for (SliccInPortDecl p : PsiTreeUtil.collectElementsOfType(root, SliccInPortDecl.class)) {
            addBlockFold(p, document, descriptors);
        }
        for (SliccActionDecl a : PsiTreeUtil.collectElementsOfType(root, SliccActionDecl.class)) {
            addBlockFold(a, document, descriptors);
        }
        for (SliccFunctionDecl f : PsiTreeUtil.collectElementsOfType(root, SliccFunctionDecl.class)) {
            addBlockFold(f, document, descriptors);
        }
        for (PsiElement e : PsiTreeUtil.collectElements(root,
                el -> el.getNode().getElementType() ==
                        com.slicc.plugin.lexer.SliccTokenTypes.BLOCK_COMMENT)) {
            TextRange range = e.getTextRange();
            if (range.getLength() > 20) {
                descriptors.add(new FoldingDescriptor(e.getNode(), range));
            }
        }

        return descriptors.toArray(new FoldingDescriptor[0]);
    }

    private void addBlockFold(PsiElement element,
                              Document document,
                              List<FoldingDescriptor> out) {
        TextRange range = element.getTextRange();
        if (range.getLength() < 10) return;
        int startLine = document.getLineNumber(range.getStartOffset());
        int endLine   = document.getLineNumber(range.getEndOffset());
        if (endLine > startLine) {
            out.add(new FoldingDescriptor(element.getNode(), range));
        }
    }

    @Nullable
    @Override
    public String getPlaceholderText(@NotNull ASTNode node) {
        PsiElement psi = node.getPsi();
        if (psi instanceof SliccMachineDecl m)   return "machine(" + m.getName() + ") { ? }";
        if (psi instanceof SliccInPortDecl p)     return "in_port(" + p.getName() + ") { ? }";
        if (psi instanceof SliccActionDecl a)     return "action(" + a.getName() + ") { ? }";
        if (psi instanceof SliccFunctionDecl f)   return f.getName() + "(...) { ? }";
        String text = node.getText();
        if (text.startsWith("/*")) return "/* ? */";
        return "{ ? }";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return false;
    }
}
