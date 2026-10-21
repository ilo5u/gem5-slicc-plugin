package com.slicc.plugin.navigation;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.lexer.SliccBuiltinFunctions;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.psi.SliccEnumerationDecl;
import com.slicc.plugin.psi.SliccExternalTypeDecl;
import com.slicc.plugin.psi.SliccFieldDecl;
import com.slicc.plugin.psi.SliccFunctionDecl;
import com.slicc.plugin.psi.SliccMachineDecl;
import com.slicc.plugin.psi.SliccNamedElement;
import com.slicc.plugin.psi.SliccPsiUtil;
import com.slicc.plugin.psi.SliccStateDecl;
import com.slicc.plugin.psi.SliccStructureDecl;
import com.slicc.plugin.psi.SliccTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class SliccCallDiagnostics {
    private static final Set<String> SCALAR_CONSTRUCTORS = Set.of("bool", "char", "int", "float", "double",
            "unsigned", "long", "string");

    private SliccCallDiagnostics() {}

    @Nullable
    public static String error(@NotNull PsiElement callName) {
        ASTNode node = callName.getNode();
        if (node == null || node.getElementType() != SliccTokenTypes.IDENTIFIER
                || DumbService.isDumb(callName.getProject())) return null;
        ASTNode next = SliccPsiUtil.nextSignificantSibling(node);
        if (next == null || next.getElementType() != SliccTokenTypes.OP_LPAREN || !completeCall(next)) return null;
        if (callName.getParent().getNode().getElementType() == SliccTypes.TYPE_REF) return null;
        SliccFunctionDecl enclosing = PsiTreeUtil.getParentOfType(callName, SliccFunctionDecl.class);
        if (enclosing != null && callName.equals(enclosing.getNameIdentifier())) return null;
        ASTNode previous = SliccPsiUtil.previousSignificantSibling(node);
        if (previous != null && (previous.getElementType() == SliccTokenTypes.OP_DOT
                || previous.getElementType() == SliccTokenTypes.OP_COLON
                || previous.getElementType() == SliccTokenTypes.KW_NEW)) return null;
        SliccFunctionNavigation.Scope scope = new SliccFunctionNavigation.Scope(callName);
        if (scope.hasAmbiguousOwners()) return null;
        String name = callName.getText();
        if (previous != null && previous.getElementType() == SliccTokenTypes.OP_SCOPE) {
            String owner = scopedOwner(previous);
            TypeLookup type = owner == null ? null : type(scope, owner);
            if (type == null || type.ambiguous() || type.declaration() == null) return null;
            Boolean exists = member(scope, type.declaration(), name);
            return Boolean.FALSE.equals(exists) ? "Type '" + owner + "' has no function '" + name + "'" : null;
        }
        if (SliccBuiltinFunctions.isDefaultName(name) || SCALAR_CONSTRUCTORS.contains(name)
                || SliccMessageScope.isMessageVariable(name) && SliccMessageScope.isVisible(name, callName)) return null;
        for (SliccFieldDecl variable : SliccGotoDeclarationHandler.collectVisibleVariables(callName)) {
            if (name.equals(variable.getName())) return null;
        }
        TypeLookup constructor = type(scope, name);
        if (constructor.ambiguous() || constructor.declaration() != null || generated(scope, name)) return null;
        SliccNamedElement owner = PsiTreeUtil.getParentOfType(callName,
                SliccStructureDecl.class, SliccExternalTypeDecl.class);
        if (owner != null && !Boolean.FALSE.equals(member(scope, owner, name))) return null;
        return scope.freeFunctions(name).isEmpty() ? "No function '" + name + "' is visible in this scope" : null;
    }

    @Nullable
    private static Boolean member(SliccFunctionNavigation.Scope scope, SliccNamedElement owner, String name) {
        Set<SliccNamedElement> visited = new HashSet<>();
        SliccNamedElement current = owner;
        while (current != null && visited.add(current)) {
            ProgressManager.checkCanceled();
            if (!wellFormed(current)) return null;
            for (PsiElement child : current.getChildren()) {
                if (child instanceof SliccFunctionDecl function && name.equals(function.getName())) return true;
            }
            String parent = current instanceof SliccStructureDecl structure
                    ? SliccGotoDeclarationHandler.getStructureInterface(structure) : null;
            if (parent == null) return hasInterface(current) ? null : false;
            TypeLookup inherited = type(scope, parent);
            if (inherited.ambiguous() || inherited.declaration() == null) return null;
            current = inherited.declaration();
        }
        return null;
    }

    private record TypeLookup(@Nullable SliccNamedElement declaration, boolean ambiguous) {}

    private static TypeLookup type(SliccFunctionNavigation.Scope scope, String name) {
        SliccNamedElement selected = null;
        int bestRank = Integer.MAX_VALUE;
        boolean ambiguous = false;
        for (PsiFile file : scope.files) {
            for (SliccNamedElement candidate : SliccFileSymbols.named(file, name)) {
                ProgressManager.checkCanceled();
                if (!(candidate instanceof SliccStructureDecl || candidate instanceof SliccExternalTypeDecl
                        || candidate instanceof SliccEnumerationDecl || candidate instanceof SliccStateDecl)) continue;
                int rank = scope.rank(candidate);
                if (rank < 0 || rank > bestRank) continue;
                if (rank < bestRank) {
                    selected = candidate;
                    bestRank = rank;
                    ambiguous = false;
                } else if (selected != candidate) {
                    ambiguous = true;
                }
            }
        }
        return new TypeLookup(selected, ambiguous);
    }

    private static boolean generated(SliccFunctionNavigation.Scope scope, String name) {
        if (!name.contains("_")) return false;
        for (PsiFile file : scope.files) {
            for (SliccMachineDecl machine : SliccFileSymbols.ofType(file, SliccMachineDecl.class)) {
                if (scope.rank(machine) >= 0 && machine.getName() != null
                        && name.startsWith(machine.getName() + "_")) return true;
            }
        }
        return false;
    }

    private static boolean completeCall(ASTNode opening) {
        int depth = 0;
        for (ASTNode token = opening; token != null; token = token.getTreeNext()) {
            ProgressManager.checkCanceled();
            if (token.getPsi() instanceof PsiErrorElement) return false;
            if (token.getElementType() == SliccTokenTypes.OP_LPAREN) depth++;
            else if (token.getElementType() == SliccTokenTypes.OP_RPAREN && --depth == 0) return true;
            else if (token.getElementType() == SliccTokenTypes.OP_SEMICOLON && depth > 0) return false;
        }
        return false;
    }

    @Nullable
    private static String scopedOwner(ASTNode separator) {
        StringBuilder owner = new StringBuilder();
        ASTNode current = SliccPsiUtil.previousSignificantSibling(separator);
        while (current != null && current.getElementType() == SliccTokenTypes.IDENTIFIER) {
            owner.insert(0, current.getText());
            ASTNode previous = SliccPsiUtil.previousSignificantSibling(current);
            if (previous == null || previous.getElementType() != SliccTokenTypes.OP_SCOPE) return owner.toString();
            owner.insert(0, "::");
            current = SliccPsiUtil.previousSignificantSibling(previous);
        }
        return null;
    }

    private static boolean hasInterface(SliccNamedElement declaration) {
        ASTNode attributes = declaration.getNode().findChildByType(SliccTypes.ATTRIBUTE_LIST);
        if (attributes == null) return false;
        for (ASTNode attribute = attributes.getFirstChildNode(); attribute != null; attribute = attribute.getTreeNext()) {
            ASTNode key = attribute.getFirstChildNode();
            while (key != null && SliccPsiUtil.isTrivia(key)) key = key.getTreeNext();
            if (attribute.getElementType() == SliccTypes.ATTRIBUTE && key != null
                    && key.getElementType() == SliccTokenTypes.KW_INTERFACE) return true;
        }
        return false;
    }

    private static boolean wellFormed(SliccNamedElement declaration) {
        if (PsiTreeUtil.findChildOfType(declaration, PsiErrorElement.class) != null) return false;
        boolean body = false;
        boolean closed = false;
        for (ASTNode child = declaration.getNode().getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getElementType() == SliccTokenTypes.OP_LBRACE) body = true;
            else if (child.getElementType() == SliccTokenTypes.OP_RBRACE) closed = true;
            else if (body && !closed && !SliccPsiUtil.isTrivia(child)
                    && !(child.getPsi() instanceof SliccFieldDecl || child.getPsi() instanceof SliccFunctionDecl)) return false;
        }
        return declaration instanceof SliccExternalTypeDecl && !body || body && closed;
    }
}
