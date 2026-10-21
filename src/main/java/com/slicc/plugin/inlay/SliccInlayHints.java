package com.slicc.plugin.inlay;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.navigation.SliccFunctionNavigation;
import com.slicc.plugin.navigation.SliccGotoDeclarationHandler;
import com.slicc.plugin.navigation.SliccMessageScope;
import com.slicc.plugin.psi.SliccActionDecl;
import com.slicc.plugin.psi.SliccFieldDecl;
import com.slicc.plugin.psi.SliccFunctionDecl;
import com.slicc.plugin.psi.SliccPsiUtil;
import com.slicc.plugin.psi.SliccTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SliccInlayHints {
    private SliccInlayHints() {}

    public record Hint(int offset, String text, boolean relatesToPrecedingText) {}

    private record Argument(ASTNode first, ASTNode last) {}

    @NotNull
    public static List<Hint> parameterHints(@NotNull PsiElement element) {
        if (!isExpressionIdentifier(element)) return List.of();
        ASTNode opening = SliccPsiUtil.nextSignificantSibling(element.getNode());
        if (opening == null || opening.getElementType() != SliccTokenTypes.OP_LPAREN) return List.of();
        List<Argument> arguments = arguments(opening);
        if (arguments == null || arguments.isEmpty()) return List.of();
        ASTNode previous = SliccPsiUtil.previousSignificantSibling(element.getNode());
        if (previous == null || previous.getElementType() != SliccTokenTypes.OP_DOT
                && previous.getElementType() != SliccTokenTypes.OP_SCOPE) {
            for (SliccFieldDecl variable : SliccGotoDeclarationHandler.collectVisibleVariables(element)) {
                if (element.getText().equals(variable.getName())) return List.of();
            }
        }
        List<SliccFunctionDecl> functions = SliccFunctionNavigation.resolveCall(element);
        if (functions.isEmpty()) return List.of();
        List<List<String>> signatures = new ArrayList<>();
        for (SliccFunctionDecl function : functions) {
            ProgressManager.checkCanceled();
            List<String> names = parameterNames(function);
            if (names.size() != arguments.size()) return List.of();
            signatures.add(names);
        }
        List<Hint> hints = new ArrayList<>();
        for (int index = 0; index < arguments.size(); index++) {
            String name = signatures.getFirst().get(index);
            if (name == null || name.isBlank() || "_".equals(name)) continue;
            boolean consistent = true;
            for (List<String> signature : signatures) {
                if (!name.equals(signature.get(index))) {
                    consistent = false;
                    break;
                }
            }
            Argument argument = arguments.get(index);
            if (!consistent || argument.first() == argument.last() && name.equals(argument.first().getText())) continue;
            hints.add(new Hint(argument.first().getStartOffset(), name + ":", false));
        }
        return hints;
    }

    private static List<String> parameterNames(SliccFunctionDecl function) {
        List<String> names = new ArrayList<>();
        ASTNode parameters = function.getNode().findChildByType(SliccTypes.PARAM_LIST);
        if (parameters == null) return names;
        for (ASTNode parameter = parameters.getFirstChildNode(); parameter != null; parameter = parameter.getTreeNext()) {
            if (parameter.getPsi() instanceof SliccFieldDecl field) names.add(field.getName());
        }
        return names;
    }

    @Nullable
    private static List<Argument> arguments(ASTNode opening) {
        List<Argument> arguments = new ArrayList<>();
        ArrayDeque<IElementType> endings = new ArrayDeque<>();
        ASTNode first = null;
        ASTNode last = null;
        for (ASTNode token = opening.getTreeNext(); token != null; token = token.getTreeNext()) {
            ProgressManager.checkCanceled();
            if (SliccPsiUtil.isTrivia(token)) continue;
            IElementType type = token.getElementType();
            if (type == SliccTokenTypes.OP_SEMICOLON || type == SliccTypes.BLOCK) return null;
            if (endings.isEmpty() && type == SliccTokenTypes.OP_RPAREN) {
                if (first != null) arguments.add(new Argument(first, last));
                else if (!arguments.isEmpty()) return null;
                return arguments;
            }
            if (endings.isEmpty() && type == SliccTokenTypes.OP_COMMA) {
                if (first == null) return null;
                arguments.add(new Argument(first, last));
                first = null;
                last = null;
                continue;
            }
            if (type == SliccTokenTypes.OP_LPAREN) endings.push(SliccTokenTypes.OP_RPAREN);
            else if (type == SliccTokenTypes.OP_LBRACKET) endings.push(SliccTokenTypes.OP_RBRACKET);
            else if (type == SliccTokenTypes.OP_LBRACE) endings.push(SliccTokenTypes.OP_RBRACE);
            else if (type == SliccTokenTypes.OP_RPAREN || type == SliccTokenTypes.OP_RBRACKET
                    || type == SliccTokenTypes.OP_RBRACE) {
                if (endings.isEmpty() || endings.pop() != type) return null;
            }
            if (first == null) first = token;
            last = token;
        }
        return null;
    }

    private static boolean isExpressionIdentifier(PsiElement element) {
        ASTNode node = element.getNode();
        if (node == null || element.getFirstChild() != null
                || node.getElementType() != SliccTokenTypes.IDENTIFIER) return false;
        for (PsiElement current = element.getParent(); current != null && !(current instanceof PsiFile);
             current = current.getParent()) {
            IElementType type = current.getNode().getElementType();
            if (type == SliccTypes.TYPE_REF || type == SliccTypes.ATTRIBUTE_LIST
                    || type == SliccTypes.PARAM_LIST) return false;
            if (type == SliccTypes.STATEMENT) return true;
            if (current instanceof SliccFieldDecl) {
                ASTNode assignment = current.getNode().findChildByType(SliccTokenTypes.OP_ASSIGN);
                return assignment != null && assignment.getStartOffset() < element.getTextOffset();
            }
        }
        return false;
    }

    public static final class TypeCollector {
        private final Map<PsiElement, Set<String>> shownBindings = new HashMap<>();

        @NotNull
        public List<Hint> collect(@NotNull PsiElement element) {
            String name = element.getText();
            boolean message = SliccMessageScope.isMessageVariable(name);
            if (!message && !"tbe".equals(name) && !"cache_entry".equals(name) && !"address".equals(name)) {
                return List.of();
            }
            if (!isExpressionIdentifier(element)) return List.of();
            ASTNode previous = SliccPsiUtil.previousSignificantSibling(element.getNode());
            ASTNode next = SliccPsiUtil.nextSignificantSibling(element.getNode());
            if (previous != null && (previous.getElementType() == SliccTokenTypes.OP_DOT
                    || previous.getElementType() == SliccTokenTypes.OP_SCOPE
                    || previous.getElementType() == SliccTokenTypes.OP_COLON)) return List.of();
            if (next != null && (next.getElementType() == SliccTokenTypes.OP_LPAREN
                    || next.getElementType() == SliccTokenTypes.OP_SCOPE
                    || next.getElementType() == SliccTokenTypes.OP_COLON)) return List.of();
            PsiElement binding = message ? SliccMessageScope.findTypeReference(name, element)
                    : PsiTreeUtil.getParentOfType(element, SliccActionDecl.class);
            if (binding == null) return List.of();
            Set<String> shown = shownBindings.get(binding);
            if (shown != null && shown.contains(name)) return List.of();
            for (SliccFieldDecl variable : SliccGotoDeclarationHandler.collectVisibleVariables(element)) {
                if (name.equals(variable.getName())) return List.of();
            }
            String type = message ? SliccMessageScope.resolveType(name, element)
                    : SliccGotoDeclarationHandler.resolveOwnerTypeForCompletion(element.getNode(), element);
            if (type == null || type.isBlank()) return List.of();
            shownBindings.computeIfAbsent(binding, unused -> new HashSet<>()).add(name);
            return List.of(new Hint(element.getTextRange().getEndOffset(), ": " + type, true));
        }
    }
}
