package com.slicc.plugin.navigation;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.icons.SliccIcons;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class SliccMemberNavigation {

    private SliccMemberNavigation() {}

    public static boolean supports(SliccNamedElement declaration) {
        return declaration instanceof SliccEnumMember
                || declaration instanceof SliccFieldDecl && declaration.getParent() instanceof SliccStructureDecl;
    }

    @NotNull
    public static SliccUsageSite[] findUsages(@NotNull SliccNamedElement declaration) {
        String name = declaration.getName();
        if (!supports(declaration) || name == null) return new SliccUsageSite[0];
        Set<PsiFile> files = new LinkedHashSet<>(new SliccFunctionNavigation.Scope(declaration).files);
        files.addAll(SliccProtocolIndex.allProjectFiles(declaration.getProject()));
        Set<PsiElement> statements = new LinkedHashSet<>();
        List<SliccUsageSite> usages = new ArrayList<>();
        PsiManager manager = declaration.getManager();
        for (PsiFile file : files) {
            for (PsiElement reference : PsiTreeUtil.collectElements(file,
                    element -> element.getNode().getElementType() == SliccTokenTypes.IDENTIFIER
                            && name.equals(element.getText()))) {
                ProgressManager.checkCanceled();
                if (isDeclarationName(reference)) continue;
                PsiElement resolved = SliccGotoDeclarationHandler.resolveReference(reference);
                if (resolved != null && manager.areElementsEquivalent(declaration, resolved)
                        && statements.add(SliccUsageSite.statementOf(reference))) {
                    usages.add(new SliccUsageSite(reference,
                            declaration instanceof SliccEnumMember ? SliccIcons.ENUM : SliccIcons.FILE));
                }
            }
        }
        return usages.toArray(SliccUsageSite[]::new);
    }

    private static boolean isDeclarationName(PsiElement reference) {
        for (PsiElement parent = reference.getParent(); parent != null && !(parent instanceof PsiFile);
             parent = parent.getParent()) {
            if (parent instanceof SliccNamedElement named && !(named instanceof SliccMachineDecl)
                    && reference.equals(named.getNameIdentifier())) return true;
        }
        return false;
    }

    @Nullable
    public static SliccFieldDecl resolveField(String type, String name, PsiElement context) {
        SliccFunctionNavigation.Scope scope = new SliccFunctionNavigation.Scope(context);
        Set<String> visited = new HashSet<>();
        String current = type;
        while (current != null && visited.add(current)) {
            SliccNamedElement owner = scope.typeDeclaration(current, false);
            if (owner == null) return null;
            for (PsiElement child : owner.getChildren()) {
                if (child instanceof SliccFieldDecl field && name.equals(field.getName())) return field;
            }
            current = owner instanceof SliccStructureDecl structure
                    ? SliccGotoDeclarationHandler.getStructureInterface(structure) : null;
        }
        return null;
    }

    public static List<SliccFieldDecl> visibleFields(SliccStructureDecl structure, PsiElement context) {
        SliccFunctionNavigation.Scope scope = new SliccFunctionNavigation.Scope(context);
        List<SliccFieldDecl> result = new ArrayList<>();
        Set<PsiElement> visited = new HashSet<>();
        SliccNamedElement owner = structure;
        while (owner != null && visited.add(owner)) {
            for (PsiElement child : owner.getChildren()) {
                if (child instanceof SliccFieldDecl field) result.add(field);
            }
            String parent = owner instanceof SliccStructureDecl current
                    ? SliccGotoDeclarationHandler.getStructureInterface(current) : null;
            owner = parent != null ? scope.typeDeclaration(parent, false) : null;
        }
        return result;
    }

    @Nullable
    public static SliccEnumMember resolveConstant(@Nullable String type, String name, PsiElement context) {
        SliccFunctionNavigation.Scope scope = new SliccFunctionNavigation.Scope(context);
        if (type != null) {
            SliccNamedElement owner = scope.typeDeclaration(type, true);
            return owner == null ? null : constantIn(owner, name);
        }
        PsiElement parent = context.getParent();
        if (parent != null && parent.getNode().getElementType() == SliccTypes.TRANSITION_STATES) {
            return resolveConstant("State", name, context);
        }
        if (parent != null && parent.getNode().getElementType() == SliccTypes.TRANSITION_EVENTS) {
            return resolveConstant("Event", name, context);
        }
        SliccEnumMember selected = null;
        int bestRank = Integer.MAX_VALUE;
        boolean ambiguous = false;
        for (PsiFile file : scope.files) {
            for (SliccEnumMember member : SliccFileSymbols.named(file, name, SliccEnumMember.class)) {
                ProgressManager.checkCanceled();
                int rank = scope.rank(member);
                if (rank < 0 || rank > bestRank) continue;
                if (rank == bestRank && selected != null) {
                    ambiguous = true;
                    continue;
                }
                selected = member;
                bestRank = rank;
                ambiguous = false;
            }
        }
        return ambiguous ? null : selected;
    }

    private static SliccEnumMember constantIn(SliccNamedElement owner, String name) {
        for (PsiElement child : owner.getChildren()) {
            if (child instanceof SliccEnumMember member && name.equals(member.getName())) return member;
        }
        return null;
    }
}
