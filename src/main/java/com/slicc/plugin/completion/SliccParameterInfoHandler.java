package com.slicc.plugin.completion;

import com.intellij.lang.parameterInfo.*;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.navigation.SliccGotoDeclarationHandler;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows a parameter info popup (Ctrl+P) when the cursor is inside a function call.
 * Lists all overloads and highlights the current argument position.
 */
public class SliccParameterInfoHandler implements ParameterInfoHandler<PsiElement, SliccFunctionDecl> {

    @Override
    @Nullable
    public PsiElement findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
        PsiElement at = context.getFile().findElementAt(context.getOffset());
        System.err.println("[SLICC-PI] findElementForParameterInfo offset=" + context.getOffset()
                + " at=" + (at != null ? at.getClass().getSimpleName() + "/" + at.getText() : "null"));
        PsiElement lparen = findEnclosingLParen(at);
        System.err.println("[SLICC-PI] lparen=" + (lparen != null ? lparen.getText() : "null"));
        if (lparen == null) return null;

        // Check for dot-access: owner.func(  or plain func(
        String ownerType = resolveDotOwner(lparen, context.getFile());
        String funcName = findCallName(lparen);
        System.err.println("[SLICC-PI] funcName=" + funcName + " ownerType=" + ownerType);
        if (funcName == null) return null;

        PsiElement callName = findCallNameElement(lparen);
        com.intellij.lang.ASTNode previous = callName == null ? null
                : SliccPsiUtil.previousSignificantSibling(callName.getNode());
        boolean member = previous != null && previous.getElementType() == SliccTokenTypes.OP_DOT;
        List<SliccFunctionDecl> overloads = ownerType != null
                ? SliccGotoDeclarationHandler.collectMemberFunctionOverloadsInContext(ownerType, funcName, callName)
                : member ? List.of() : SliccGotoDeclarationHandler.collectFunctionOverloads(funcName, context.getFile());
        System.err.println("[SLICC-PI] overloads.size=" + overloads.size());
        if (overloads.isEmpty()) return null;

        context.setItemsToShow(overloads.toArray(new SliccFunctionDecl[0]));
        return lparen;
    }

    @Override
    public void showParameterInfo(@NotNull PsiElement element, @NotNull CreateParameterInfoContext context) {
        context.showHint(element, element.getTextOffset(), this);
    }

    @Override
    @Nullable
    public PsiElement findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
        PsiElement at = context.getFile().findElementAt(context.getOffset());
        return findEnclosingLParen(at);
    }

    @Override
    public void updateParameterInfo(@NotNull PsiElement lparen, @NotNull UpdateParameterInfoContext context) {
        int commas = countCommasBetween(lparen.getTextOffset() + 1, context.getOffset(), context.getFile());
        context.setCurrentParameter(commas);
    }

    @Override
    public void updateUI(SliccFunctionDecl fn, @NotNull ParameterInfoUIContext context) {
        List<String[]> params = getParams(fn);
        if (params.isEmpty()) {
            context.setupUIComponentPresentation("(no parameters)", -1, -1,
                    false, false, false, context.getDefaultParameterColor());
            return;
        }
        StringBuilder sb = new StringBuilder();
        int highlightStart = -1, highlightEnd = -1;
        int idx = context.getCurrentParameterIndex();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            int start = sb.length();
            sb.append(params.get(i)[0]);
            if (!params.get(i)[1].isEmpty()) sb.append(" ").append(params.get(i)[1]);
            if (i == idx) {
                highlightStart = start;
                highlightEnd = sb.length();
            }
        }
        context.setupUIComponentPresentation(sb.toString(), highlightStart, highlightEnd,
                false, false, false, context.getDefaultParameterColor());
    }

    // ---- helpers ----

    @Nullable
    private static PsiElement findEnclosingLParen(@Nullable PsiElement at) {
        // If we start on a ) (e.g. cursor between empty parens or just after auto-inserted )),
        // skip it so we don't count it as a nested pair that hides the enclosing (.
        PsiElement cur = at;
        if (cur != null && cur.getNode() != null
                && cur.getNode().getElementType() == SliccTokenTypes.OP_RPAREN) {
            cur = PsiTreeUtil.prevLeaf(cur);
        }
        int depth = 0;
        while (cur != null) {
            IElementType t = cur.getNode() != null ? cur.getNode().getElementType() : null;
            if (t == SliccTokenTypes.OP_RPAREN) depth++;
            else if (t == SliccTokenTypes.OP_LPAREN) {
                if (depth == 0) return cur;
                depth--;
            }
            cur = PsiTreeUtil.prevLeaf(cur);
        }
        return null;
    }

    @Nullable
    private static String findCallName(@NotNull PsiElement lparen) {
        PsiElement identifier = findCallNameElement(lparen);
        return identifier == null ? null : identifier.getText();
    }

    @Nullable
    private static PsiElement findCallNameElement(PsiElement lparen) {
        com.intellij.lang.ASTNode name = SliccPsiUtil.previousSignificantSibling(lparen.getNode());
        return name != null && name.getElementType() == SliccTokenTypes.IDENTIFIER ? name.getPsi() : null;
    }

    /** If the call is owner.func(, resolve owner's type; otherwise null. */
    @Nullable
    private static String resolveDotOwner(@NotNull PsiElement lparen, @NotNull PsiFile file) {
        PsiElement funcName = findCallNameElement(lparen);
        if (funcName == null) return null;
        com.intellij.lang.ASTNode dot = SliccPsiUtil.previousSignificantSibling(funcName.getNode());
        if (dot == null || dot.getElementType() != SliccTokenTypes.OP_DOT) return null;
        com.intellij.lang.ASTNode owner = SliccPsiUtil.previousSignificantSibling(dot);
        if (owner == null || owner.getElementType() != SliccTokenTypes.IDENTIFIER) return null;
        return SliccGotoDeclarationHandler.resolveOwnerTypeForCompletion(owner, funcName, file);
    }

    private static int countCommasBetween(int start, int end, @NotNull PsiFile file) {
        int count = 0, depth = 0;
        PsiElement el = file.findElementAt(start);
        while (el != null && el.getTextOffset() < end) {
            IElementType t = el.getNode() != null ? el.getNode().getElementType() : null;
            if (t == SliccTokenTypes.OP_LPAREN) depth++;
            else if (t == SliccTokenTypes.OP_RPAREN) { if (depth > 0) depth--; }
            else if (t == SliccTokenTypes.OP_COMMA && depth == 0) count++;
            el = PsiTreeUtil.nextLeaf(el);
        }
        return count;
    }

    private static List<String[]> getParams(@NotNull SliccFunctionDecl fn) {
        List<String[]> result = new ArrayList<>();
        com.intellij.lang.ASTNode paramList = fn.getNode().findChildByType(SliccTypes.PARAM_LIST);
        if (paramList == null) return result;
        for (com.intellij.lang.ASTNode ch = paramList.getFirstChildNode(); ch != null; ch = ch.getTreeNext()) {
            if (ch.getElementType() != SliccTypes.FIELD_DECL) continue;
            if (!(ch.getPsi() instanceof SliccFieldDecl field)) continue;
            String typeName = "";
            com.intellij.lang.ASTNode tr = field.getNode().findChildByType(SliccTypes.TYPE_REF);
            if (tr != null) typeName = SliccPsiUtil.typeText(tr);
            String paramName = field.getName() != null ? field.getName() : "";
            result.add(new String[]{typeName, paramName});
        }
        return result;
    }
}
