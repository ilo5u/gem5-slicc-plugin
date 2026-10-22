package com.slicc.plugin.navigation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.psi.SliccActionDecl;
import com.slicc.plugin.psi.SliccExternalTypeDecl;
import com.slicc.plugin.psi.SliccFieldDecl;
import com.slicc.plugin.psi.SliccFunctionDecl;
import com.slicc.plugin.psi.SliccMachineDecl;
import com.slicc.plugin.psi.SliccNamedElement;
import com.slicc.plugin.psi.SliccPsiUtil;
import com.slicc.plugin.psi.SliccStructureDecl;
import com.slicc.plugin.psi.SliccTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SliccMemberDiagnostics {
    private static final Key<Map<PsiElement, Context>> CONTEXTS = Key.create("SLICC_MEMBER_DIAGNOSTICS");

    private SliccMemberDiagnostics() {}

    @Nullable
    public static String error(@NotNull PsiElement member, @Nullable AnnotationSession session) {
        ASTNode node = member.getNode();
        if (node == null || node.getElementType() != SliccTokenTypes.IDENTIFIER
                || DumbService.isDumb(member.getProject())) return null;
        ASTNode dot = SliccPsiUtil.previousSignificantSibling(node);
        if (dot == null || dot.getElementType() != SliccTokenTypes.OP_DOT) return null;
        ASTNode receiver = SliccPsiUtil.previousSignificantSibling(dot);
        if (receiver == null || receiver.getElementType() != SliccTokenTypes.IDENTIFIER) return null;
        Context context = context(member, session);
        if (context.scope.hasAmbiguousOwners()) return null;
        String type = context.receiverType(receiver, member);
        if (type == null) return null;
        Members members = context.members(type);
        return members.complete() && !members.names().contains(member.getText())
                ? "Type '" + type + "' has no member '" + member.getText() + "'" : null;
    }

    private static Context context(PsiElement member, AnnotationSession session) {
        if (session == null) return new Context(member);
        Map<PsiElement, Context> contexts = session.getUserData(CONTEXTS);
        if (contexts == null) {
            contexts = new HashMap<>();
            session.putUserData(CONTEXTS, contexts);
        }
        PsiElement owner = PsiTreeUtil.getParentOfType(member, SliccMachineDecl.class);
        if (owner == null) owner = member.getContainingFile();
        return contexts.computeIfAbsent(owner, unused -> new Context(member));
    }

    private record Members(Map<String, String> fields, Set<String> names, boolean complete) {}

    private static final class Context {
        private final SliccFunctionNavigation.Scope scope;
        private final Map<String, Members> tables = new HashMap<>();

        private Context(PsiElement member) {
            scope = new SliccFunctionNavigation.Scope(member);
        }

        @Nullable
        private String receiverType(ASTNode receiver, PsiElement position) {
            ProgressManager.checkCanceled();
            ASTNode previous = SliccPsiUtil.previousSignificantSibling(receiver);
            if (previous != null && previous.getElementType() == SliccTokenTypes.OP_DOT) {
                ASTNode owner = SliccPsiUtil.previousSignificantSibling(previous);
                if (owner == null || owner.getElementType() != SliccTokenTypes.IDENTIFIER) return null;
                String ownerType = receiverType(owner, position);
                if (ownerType == null) return null;
                Members ownerMembers = members(ownerType);
                if (!ownerMembers.complete()) return null;
                return SliccPsiUtil.baseTypeName(ownerMembers.fields().get(receiver.getText()));
            }
            if (previous != null && (previous.getElementType() == SliccTokenTypes.OP_SCOPE
                    || previous.getElementType() == SliccTokenTypes.OP_COLON)) return null;
            String name = receiver.getText();
            if (SliccMessageScope.isMessageVariable(name)) {
                return SliccPsiUtil.baseTypeName(SliccMessageScope.resolveType(name, position));
            }
            for (SliccFieldDecl variable : SliccGotoDeclarationHandler.collectVisibleVariables(position)) {
                if (name.equals(variable.getName())) {
                    if (variable.getParent() instanceof SliccStructureDecl declaration) {
                        SliccStructureDecl enclosing = PsiTreeUtil.getParentOfType(position, SliccStructureDecl.class);
                        SliccStructureDecl owner = enclosing != null ? enclosing : declaration;
                        if (owner.getName() == null || uniqueType(owner.getName()) != owner) return null;
                        Members visible = members(owner.getName());
                        return visible.complete() ? SliccPsiUtil.baseTypeName(visible.fields().get(name)) : null;
                    }
                    return SliccPsiUtil.baseTypeName(SliccPsiUtil.typeText(
                            variable.getNode().findChildByType(SliccTypes.TYPE_REF)));
                }
            }
            if (("tbe".equals(name) || "cache_entry".equals(name) || "address".equals(name))
                    && PsiTreeUtil.getParentOfType(position, SliccActionDecl.class) != null) {
                return SliccGotoDeclarationHandler.resolveOwnerTypeForCompletion(receiver, position);
            }
            return SliccPorts.resolve(name, position).type();
        }

        private Members members(String type) {
            return tables.computeIfAbsent(type, this::collectMembers);
        }

        private Members collectMembers(String type) {
            Map<String, String> fields = new HashMap<>();
            Set<String> names = new HashSet<>();
            Set<String> visited = new HashSet<>();
            String current = type;
            while (current != null && visited.add(current)) {
                ProgressManager.checkCanceled();
                SliccNamedElement declaration = uniqueType(current);
                if (declaration == null || !wellFormed(declaration)) return new Members(fields, names, false);
                for (PsiElement child : declaration.getChildren()) {
                    if (child instanceof SliccFieldDecl field) {
                        String name = field.getName();
                        String fieldType = SliccPsiUtil.typeText(field.getNode().findChildByType(SliccTypes.TYPE_REF));
                        if (name == null || fieldType.isBlank()) return new Members(fields, names, false);
                        fields.putIfAbsent(name, fieldType);
                        names.add(name);
                    } else if (child instanceof SliccFunctionDecl function) {
                        String name = function.getName();
                        if (name == null) return new Members(fields, names, false);
                        names.add(name);
                    }
                }
                String parent = declaration instanceof SliccStructureDecl structure
                        ? SliccGotoDeclarationHandler.getStructureInterface(structure) : null;
                if (parent == null && hasInterface(declaration)) return new Members(fields, names, false);
                current = parent;
            }
            return new Members(fields, names, current == null);
        }

        @Nullable
        private SliccNamedElement uniqueType(String type) {
            SliccNamedElement selected = null;
            int bestRank = Integer.MAX_VALUE;
            boolean ambiguous = false;
            for (PsiFile file : scope.files) {
                for (SliccNamedElement candidate : SliccFileSymbols.named(file, type)) {
                    ProgressManager.checkCanceled();
                    if (!(candidate instanceof SliccStructureDecl || candidate instanceof SliccExternalTypeDecl)) continue;
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
            return ambiguous ? null : selected;
        }
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

    private static boolean hasInterface(SliccNamedElement declaration) {
        ASTNode attributes = declaration.getNode().findChildByType(SliccTypes.ATTRIBUTE_LIST);
        if (attributes == null) return false;
        for (ASTNode attribute = attributes.getFirstChildNode(); attribute != null; attribute = attribute.getTreeNext()) {
            ASTNode key = attribute.getFirstChildNode();
            while (key != null && SliccPsiUtil.isTrivia(key)) key = key.getTreeNext();
            if (attribute.getElementType() == SliccTypes.ATTRIBUTE
                    && key != null && "interface".equals(key.getText())) return true;
        }
        return false;
    }
}
