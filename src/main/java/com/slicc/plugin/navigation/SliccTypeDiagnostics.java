package com.slicc.plugin.navigation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.lexer.SliccBuiltinFunctions;
import com.slicc.plugin.psi.SliccFieldDecl;
import com.slicc.plugin.psi.SliccFunctionDecl;
import com.slicc.plugin.psi.SliccMachineDecl;
import com.slicc.plugin.psi.SliccPsiUtil;
import com.slicc.plugin.psi.SliccTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SliccTypeDiagnostics {
    private static final Key<Map<PsiElement, List<Diagnostic>>> RESULTS = Key.create("SLICC_TYPE_DIAGNOSTICS");
    private static final Key<Map<PsiElement, SliccExpressionTypes.Session>> CONTEXTS = Key.create("SLICC_TYPE_CONTEXTS");

    private SliccTypeDiagnostics() {}

    public record Diagnostic(@NotNull TextRange range, @NotNull String message) {}

    @NotNull
    public static List<Diagnostic> check(@NotNull PsiElement container, @Nullable AnnotationSession session) {
        ProgressManager.checkCanceled();
        ASTNode node = container.getNode();
        if (node == null || (node.getElementType() != SliccTypes.STATEMENT
                && !(container instanceof SliccFieldDecl)) || DumbService.isDumb(container.getProject())) {
            return List.of();
        }
        if (session == null) return analyze(container, null);
        Map<PsiElement, List<Diagnostic>> results = session.getUserData(RESULTS);
        if (results == null) {
            results = new HashMap<>();
            session.putUserData(RESULTS, results);
        }
        return results.computeIfAbsent(container, unused -> analyze(container, session));
    }

    private static List<Diagnostic> analyze(PsiElement container, AnnotationSession session) {
        List<ASTNode> tokens = new ArrayList<>();
        for (ASTNode child = container.getNode().getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getPsi() instanceof PsiErrorElement) return List.of();
            collectTokens(child, tokens);
        }
        boolean containsCall = false;
        for (int index = 0; index + 1 < tokens.size(); index++) {
            if ((tokens.get(index).getElementType() == SliccTokenTypes.IDENTIFIER
                    || SliccBuiltinFunctions.isToken(tokens.get(index).getElementType()))
                    && tokens.get(index + 1).getElementType() == SliccTokenTypes.OP_LPAREN) {
                containsCall = true;
                break;
            }
        }
        if (!containsCall || !balanced(tokens)) return List.of();
        SliccExpressionTypes.Session types = context(container, session);
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (ASTNode token : tokens) {
            ProgressManager.checkCanceled();
            if (token.getElementType() != SliccTokenTypes.IDENTIFIER
                    && !SliccBuiltinFunctions.isToken(token.getElementType())) continue;
            SliccExpressionTypes.Call call = types.inspectCall(token.getPsi());
            if (call != null) checkArguments(token, call, types, diagnostics);
        }
        checkAssignment(container, tokens, types, diagnostics);
        return List.copyOf(diagnostics);
    }

    private static SliccExpressionTypes.Session context(PsiElement container, AnnotationSession session) {
        if (session == null) return new SliccExpressionTypes.Session(container);
        Map<PsiElement, SliccExpressionTypes.Session> contexts = session.getUserData(CONTEXTS);
        if (contexts == null) {
            contexts = new HashMap<>();
            session.putUserData(CONTEXTS, contexts);
        }
        PsiElement owner = PsiTreeUtil.getParentOfType(container, SliccMachineDecl.class);
        if (owner == null) owner = container.getContainingFile();
        return contexts.computeIfAbsent(owner, unused -> new SliccExpressionTypes.Session(container));
    }

    private static void checkArguments(ASTNode name, SliccExpressionTypes.Call call,
                                       SliccExpressionTypes.Session types, List<Diagnostic> diagnostics) {
        if (!call.complete() || call.candidates().isEmpty()) return;
        List<SliccExpressionTypes.Argument> arguments = call.arguments();
        for (int index = 0; index < arguments.size(); index++) {
            SliccExpressionTypes.Argument argument = arguments.get(index);
            String actual = argument.value().type();
            if (!argument.value().containsCall() || actual == null) continue;
            List<List<String>> all = new ArrayList<>();
            List<List<String>> relevant = new ArrayList<>();
            for (SliccFunctionDecl candidate : call.candidates()) {
                List<String> parameters = candidate.getParameterTypes();
                if (parameters.size() != arguments.size()) continue;
                all.add(parameters);
                if (compatibleOthers(parameters, arguments, index, types)) relevant.add(parameters);
            }
            Set<String> expected = new LinkedHashSet<>();
            boolean accepted = false;
            for (List<String> parameters : relevant.isEmpty() ? all : relevant) {
                ProgressManager.checkCanceled();
                String parameter = parameters.get(index);
                expected.add(parameter);
                if (types.compatibility(actual, parameter) != SliccExpressionTypes.Compatibility.MISMATCH) {
                    accepted = true;
                }
            }
            if (expected.isEmpty()) continue;
            if ("void".equals(SliccPsiUtil.baseTypeName(actual))) {
                diagnostics.add(new Diagnostic(argument.range(), "Void function result cannot be used as an argument"));
            } else if (!accepted) {
                diagnostics.add(new Diagnostic(argument.range(), "Function result type '" + actual
                        + "' is incompatible with parameter " + (index + 1) + " of '" + name.getText()
                        + "' (expected '" + String.join("' or '", expected) + "')"));
            }
        }
    }

    private static boolean compatibleOthers(List<String> parameters, List<SliccExpressionTypes.Argument> arguments,
                                             int target, SliccExpressionTypes.Session types) {
        for (int index = 0; index < arguments.size(); index++) {
            ProgressManager.checkCanceled();
            if (index != target && types.compatibility(arguments.get(index).value().type(), parameters.get(index))
                    == SliccExpressionTypes.Compatibility.MISMATCH) return false;
        }
        return true;
    }

    private static void checkAssignment(PsiElement container, List<ASTNode> tokens,
                                        SliccExpressionTypes.Session types, List<Diagnostic> diagnostics) {
        int assignment = -1;
        int depth = 0;
        for (int index = 0; index < tokens.size(); index++) {
            IElementType token = tokens.get(index).getElementType();
            if (opens(token)) depth++;
            else if (closes(token)) depth--;
            else if (token == SliccTokenTypes.OP_ASSIGN && depth == 0) {
                if (assignment >= 0) return;
                assignment = index;
            }
        }
        int end = tokens.size() - 1;
        if (assignment <= 0 || assignment + 1 >= end
                || tokens.get(end).getElementType() != SliccTokenTypes.OP_SEMICOLON) return;
        String expected = container instanceof SliccFieldDecl
                ? SliccPsiUtil.typeText(container.getNode().findChildByType(SliccTypes.TYPE_REF))
                : types.infer(tokens.subList(0, assignment)).type();
        if (expected == null || expected.isBlank()) return;
        List<ASTNode> expression = tokens.subList(assignment + 1, end);
        SliccExpressionTypes.Value actual = types.infer(expression);
        if (!actual.containsCall() || actual.type() == null) return;
        TextRange range = new TextRange(expression.getFirst().getStartOffset(),
                expression.getLast().getTextRange().getEndOffset());
        if ("void".equals(SliccPsiUtil.baseTypeName(actual.type()))) {
            diagnostics.add(new Diagnostic(range, "Void function result cannot be assigned to a variable"));
            return;
        }
        if ("NetDest".equals(SliccPsiUtil.baseTypeName(expected))
                || "Set".equals(SliccPsiUtil.baseTypeName(actual.type()))) return;
        if (types.compatibility(actual.type(), expected) == SliccExpressionTypes.Compatibility.MISMATCH
                && types.compatibility(expected, actual.type()) != SliccExpressionTypes.Compatibility.MATCH) {
            diagnostics.add(new Diagnostic(range, "Cannot assign function result of type '" + actual.type()
                    + "' to variable of type '" + expected + "'"));
        }
    }

    private static void collectTokens(ASTNode node, List<ASTNode> tokens) {
        IElementType type = node.getElementType();
        if (SliccPsiUtil.isTrivia(node) || type == SliccTypes.BLOCK || type == SliccTypes.STATEMENT
                || type == SliccTypes.FIELD_DECL || type == SliccTypes.TYPE_REF
                || type == SliccTypes.ATTRIBUTE_LIST) return;
        if (node.getFirstChildNode() == null) tokens.add(node);
        else {
            for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
                collectTokens(child, tokens);
            }
        }
    }

    private static boolean balanced(List<ASTNode> tokens) {
        List<IElementType> stack = new ArrayList<>();
        for (ASTNode token : tokens) {
            ProgressManager.checkCanceled();
            IElementType type = token.getElementType();
            if (opens(type)) stack.add(type);
            else if (closes(type)) {
                if (stack.isEmpty()) return false;
                IElementType opening = stack.removeLast();
                if ((type == SliccTokenTypes.OP_RPAREN && opening != SliccTokenTypes.OP_LPAREN)
                        || (type == SliccTokenTypes.OP_RBRACKET && opening != SliccTokenTypes.OP_LBRACKET)
                        || (type == SliccTokenTypes.OP_RBRACE && opening != SliccTokenTypes.OP_LBRACE)) return false;
            }
        }
        return stack.isEmpty();
    }

    private static boolean opens(IElementType type) {
        return type == SliccTokenTypes.OP_LPAREN || type == SliccTokenTypes.OP_LBRACKET
                || type == SliccTokenTypes.OP_LBRACE;
    }

    private static boolean closes(IElementType type) {
        return type == SliccTokenTypes.OP_RPAREN || type == SliccTokenTypes.OP_RBRACKET
                || type == SliccTokenTypes.OP_RBRACE;
    }
}
