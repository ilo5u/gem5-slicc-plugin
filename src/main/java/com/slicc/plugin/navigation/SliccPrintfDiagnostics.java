package com.slicc.plugin.navigation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.psi.SliccPsiUtil;
import com.slicc.plugin.psi.SliccFieldDecl;
import com.slicc.plugin.psi.SliccTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class SliccPrintfDiagnostics {
    public record Diagnostic(@NotNull TextRange range, @NotNull String message, @NotNull HighlightSeverity severity) {
        public Diagnostic(@NotNull TextRange range, @NotNull String message) {
            this(range, message, HighlightSeverity.ERROR);
        }
    }

    private record Argument(List<ASTNode> tokens) {
        private TextRange range() {
            return new TextRange(tokens.getFirst().getStartOffset(), tokens.getLast().getTextRange().getEndOffset());
        }
    }

    private SliccPrintfDiagnostics() {}

    @NotNull
    public static List<Diagnostic> checkContainer(@NotNull PsiElement container) {
        ASTNode node = container.getNode();
        if (node == null || !(container instanceof SliccFieldDecl)
                && node.getElementType() != SliccTypes.STATEMENT) return List.of();
        List<Diagnostic> diagnostics = new ArrayList<>();
        collect(node, diagnostics);
        return List.copyOf(diagnostics);
    }

    private static void collect(ASTNode node, List<Diagnostic> diagnostics) {
        ProgressManager.checkCanceled();
        if (node.getElementType() == SliccTypes.BLOCK) return;
        if (node.getElementType() == SliccTokenTypes.KW_DPRINTF
                || node.getElementType() == SliccTokenTypes.KW_DPRINTFN) {
            diagnostics.addAll(check(node.getPsi()));
        }
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) collect(child, diagnostics);
    }

    @NotNull
    public static List<Diagnostic> check(@NotNull PsiElement name) {
        ASTNode node = name.getNode();
        if (node == null) return List.of();
        boolean flagged = node.getElementType() == SliccTokenTypes.KW_DPRINTF;
        if (!flagged && node.getElementType() != SliccTokenTypes.KW_DPRINTFN) return List.of();
        if (DumbService.isDumb(name.getProject())) return List.of();
        List<Argument> arguments = arguments(node);
        if (arguments == null) return List.of();
        List<Diagnostic> diagnostics = new ArrayList<>();
        int formatIndex = flagged ? 1 : 0;
        if (flagged) {
            if (arguments.isEmpty() || arguments.getFirst().tokens().size() != 1
                    || arguments.getFirst().tokens().getFirst().getElementType() != SliccTokenTypes.IDENTIFIER) {
                return List.of(new Diagnostic(name.getTextRange(), "DPRINTF requires a debug flag before its format string"));
            }
            Argument flag = arguments.getFirst();
            SliccDebugFlags.Snapshot flags = SliccDebugFlags.snapshot(name);
            String flagName = flag.tokens().getFirst().getText();
            if (flags.sourceAvailable() && !flags.contains(flagName)) {
                diagnostics.add(new Diagnostic(flag.range(), "Unknown debug flag '" + flagName + "' in Ruby SConscript declarations"));
            }
        }
        if (arguments.size() <= formatIndex) {
            diagnostics.add(new Diagnostic(name.getTextRange(), name.getText() + " requires a format string"));
            return diagnostics;
        }
        Argument format = arguments.get(formatIndex);
        String literal = literal(format);
        if (literal == null) return diagnostics;
        Integer expected = argumentCount(literal);
        if (expected == null) return diagnostics;
        int actual = arguments.size() - formatIndex - 1;
        if (expected != actual) {
            diagnostics.add(new Diagnostic(format.range(), name.getText() + " format expects " + expected
                    + " argument(s), but " + actual + " provided", HighlightSeverity.WARNING));
        }
        return List.copyOf(diagnostics);
    }

    public static boolean isDebugFlagArgument(@NotNull PsiElement element) {
        ASTNode node = element.getNode();
        if (node == null) return false;
        ASTNode opening = SliccPsiUtil.previousSignificantSibling(node);
        if (opening == null || opening.getElementType() != SliccTokenTypes.OP_LPAREN) return false;
        ASTNode name = SliccPsiUtil.previousSignificantSibling(opening);
        return name != null && name.getElementType() == SliccTokenTypes.KW_DPRINTF;
    }

    @Nullable
    private static List<Argument> arguments(ASTNode name) {
        ASTNode opening = SliccPsiUtil.nextSignificantSibling(name);
        if (opening == null || opening.getElementType() != SliccTokenTypes.OP_LPAREN) return null;
        ArrayDeque<IElementType> endings = new ArrayDeque<>();
        List<Argument> arguments = new ArrayList<>();
        List<ASTNode> current = new ArrayList<>();
        for (ASTNode token = opening.getTreeNext(); token != null; token = token.getTreeNext()) {
            ProgressManager.checkCanceled();
            if (SliccPsiUtil.isTrivia(token)) continue;
            IElementType type = token.getElementType();
            if (endings.isEmpty() && type == SliccTokenTypes.OP_RPAREN) {
                if (!current.isEmpty()) arguments.add(new Argument(List.copyOf(current)));
                else if (!arguments.isEmpty()) return null;
                return arguments;
            }
            if (type == SliccTokenTypes.OP_SEMICOLON) return null;
            if (endings.isEmpty() && type == SliccTokenTypes.OP_COMMA) {
                if (current.isEmpty()) return null;
                arguments.add(new Argument(List.copyOf(current)));
                current.clear();
                continue;
            }
            if (type == SliccTokenTypes.OP_LPAREN) endings.push(SliccTokenTypes.OP_RPAREN);
            else if (type == SliccTokenTypes.OP_LBRACKET) endings.push(SliccTokenTypes.OP_RBRACKET);
            else if (type == SliccTokenTypes.OP_LBRACE) endings.push(SliccTokenTypes.OP_RBRACE);
            else if (type == SliccTokenTypes.OP_RPAREN || type == SliccTokenTypes.OP_RBRACKET
                    || type == SliccTokenTypes.OP_RBRACE) {
                if (endings.isEmpty() || endings.pop() != type) return null;
            }
            if (token.getFirstChildNode() != null) return null;
            current.add(token);
        }
        return null;
    }

    @Nullable
    private static String literal(Argument argument) {
        StringBuilder value = new StringBuilder();
        for (ASTNode token : argument.tokens()) {
            String text = token.getText();
            if (token.getElementType() != SliccTokenTypes.STRING_LITERAL || text.length() < 2) return null;
            char quote = text.charAt(0);
            if (text.charAt(text.length() - 1) != quote) return null;
            for (int index = 1; index < text.length() - 1; index++) {
                char character = text.charAt(index);
                if (character == '\\') {
                    if (++index >= text.length() - 1) return null;
                    character = text.charAt(index);
                    if (character == '\n') continue;
                    if (character == '\r' && index + 1 < text.length() - 1 && text.charAt(index + 1) == '\n') {
                        index++;
                        continue;
                    }
                    if ("\"'\\?abfnrtv".indexOf(character) < 0) return null;
                }
                value.append(character);
            }
        }
        return value.toString();
    }

    @Nullable
    private static Integer argumentCount(String format) {
        int count = 0;
        for (int index = 0; index < format.length(); index++) {
            if (format.charAt(index) != '%') continue;
            if (++index >= format.length()) return null;
            if (format.charAt(index) == '%') continue;
            while (index < format.length() && "-+ #0'".indexOf(format.charAt(index)) >= 0) index++;
            if (index < format.length() && format.charAt(index) == '*') {
                count++;
                index++;
            } else {
                while (index < format.length() && format.charAt(index) >= '0' && format.charAt(index) <= '9') index++;
            }
            if (index < format.length() && format.charAt(index) == '.') {
                index++;
                if (index < format.length() && format.charAt(index) == '*') {
                    count++;
                    index++;
                } else {
                    while (index < format.length() && format.charAt(index) >= '0' && format.charAt(index) <= '9') index++;
                }
            }
            if (index < format.length() && "hljztL".indexOf(format.charAt(index)) >= 0) {
                char length = format.charAt(index++);
                if ((length == 'h' || length == 'l') && index < format.length() && format.charAt(index) == length) index++;
            }
            if (index >= format.length() || "diuoxXfFeEgGaAcspnb".indexOf(format.charAt(index)) < 0) return null;
            count++;
        }
        return count;
    }
}
