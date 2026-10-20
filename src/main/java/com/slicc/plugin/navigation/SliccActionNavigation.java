package com.slicc.plugin.navigation;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

public final class SliccActionNavigation {

    private SliccActionNavigation() {}

    public static boolean isTransitionActionReference(@NotNull PsiElement element) {
        SliccTransitionDecl transition = PsiTreeUtil.getParentOfType(element, SliccTransitionDecl.class);
        if (transition == null) return false;
        ASTNode actions = transition.getNode().findChildByType(SliccTypes.TRANSITION_ACTIONS);
        return actions != null && PsiTreeUtil.isAncestor(actions.getPsi(), element, false);
    }

    @Nullable
    public static SliccActionDecl resolveAction(@NotNull PsiElement reference) {
        if (!isTransitionActionReference(reference)) return null;
        PsiFile file = reference.getContainingFile();
        if (file == null) return null;
        return resolveAction(reference, machineFiles(file));
    }

    @Nullable
    private static SliccActionDecl resolveAction(PsiElement reference, Set<PsiFile> scope) {
        String name = reference.getText();
        SliccMachineDecl machine = PsiTreeUtil.getParentOfType(reference, SliccMachineDecl.class);
        PsiManager manager = PsiManager.getInstance(reference.getProject());
        Set<PsiFile> files = new LinkedHashSet<>();
        files.add(reference.getContainingFile());
        files.addAll(scope);
        for (PsiFile candidate : files) {
            for (SliccActionDecl action : PsiTreeUtil.collectElementsOfType(candidate, SliccActionDecl.class)) {
                ProgressManager.checkCanceled();
                if (!name.equals(action.getName())) continue;
                SliccMachineDecl actionMachine = PsiTreeUtil.getParentOfType(action, SliccMachineDecl.class);
                if (machine != null && actionMachine != null
                        && !manager.areElementsEquivalent(machine, actionMachine)) continue;
                return action;
            }
        }
        return null;
    }

    @NotNull
    public static SliccTransitionDecl[] findTransitions(@NotNull SliccActionDecl action) {
        PsiFile file = action.getContainingFile();
        String name = action.getName();
        if (file == null || name == null) return new SliccTransitionDecl[0];
        Set<SliccTransitionDecl> targets = new LinkedHashSet<>();
        PsiManager manager = PsiManager.getInstance(action.getProject());
        Set<PsiFile> scope = new LinkedHashSet<>();
        scope.add(file);
        scope.addAll(SliccProtocolIndex.allMachineFilesContaining(file));
        for (PsiFile candidate : scope) {
            for (SliccTransitionDecl transition : PsiTreeUtil.collectElementsOfType(candidate, SliccTransitionDecl.class)) {
                ProgressManager.checkCanceled();
                ASTNode actions = transition.getNode().findChildByType(SliccTypes.TRANSITION_ACTIONS);
                if (actions == null) continue;
                for (PsiElement reference : PsiTreeUtil.collectElements(actions.getPsi(),
                        element -> element.getNode().getElementType() == SliccTokenTypes.IDENTIFIER
                                && name.equals(element.getText()))) {
                    SliccActionDecl resolved = resolveAction(reference, scope);
                    if (resolved != null && manager.areElementsEquivalent(action, resolved)) {
                        targets.add(transition);
                        break;
                    }
                }
            }
        }
        return targets.toArray(SliccTransitionDecl[]::new);
    }

    private static Set<PsiFile> machineFiles(PsiFile file) {
        Set<PsiFile> files = new LinkedHashSet<>();
        files.add(file);
        files.addAll(SliccProtocolIndex.machineFilesContaining(file));
        return files;
    }
}
