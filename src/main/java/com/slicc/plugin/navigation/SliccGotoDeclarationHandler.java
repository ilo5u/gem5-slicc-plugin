package com.slicc.plugin.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.lang.SliccLanguage;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SliccGotoDeclarationHandler implements GotoDeclarationHandler {

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(
            @Nullable PsiElement sourceElement,
            int offset,
            Editor editor) {

        System.err.println("[SLICC-GOTO] handler invoked, element=" +
                (sourceElement == null ? "null" : sourceElement.getClass().getSimpleName()
                        + " lang=" + sourceElement.getLanguage().getID()
                        + " text='" + sourceElement.getText() + "'"));

        if (sourceElement == null) return null;
        if (!sourceElement.getLanguage().isKindOf(SliccLanguage.INSTANCE)) return null;
        if (sourceElement.getNode().getElementType() != SliccTokenTypes.IDENTIFIER) return null;

        String key = sourceElement.getText();
        System.err.println("[SLICC-GOTO] getGotoDeclarationTargets called for key='" + key + "'");

        PsiFile file = sourceElement.getContainingFile();
        if (file == null) return null;

        // Determine if we're at a type-reference position
        boolean isTypeRef = isInTypeRefPosition(sourceElement);

        // Step 1: search containing file
        PsiElement result = searchInFile(file, key, isTypeRef, "self");
        if (result != null) return new PsiElement[]{result};

        // Step 2: protocol group
        try {
            Set<PsiFile> group = SliccProtocolIndex.groupContaining(file);
            for (PsiFile f : group) {
                result = searchInFile(f, key, isTypeRef, "protocol");
                if (result != null) return new PsiElement[]{result};
            }
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[SLICC-GOTO] group lookup error: " + e);
        }

        // Step 3: global scope
        try {
            Set<PsiFile> global = SliccProtocolIndex.globalFiles(file.getProject());
            for (PsiFile f : global) {
                result = searchInFile(f, key, isTypeRef, "global");
                if (result != null) return new PsiElement[]{result};
            }
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[SLICC-GOTO] global lookup error: " + e);
        }

        System.err.println("[SLICC-GOTO] key='" + key + "' NOT FOUND");
        return null;
    }

    private static boolean isInTypeRefPosition(PsiElement element) {
        PsiElement parent = element.getParent();
        return parent instanceof SliccCompositeElement p
                && p.getNode().getElementType() == SliccTypes.TYPE_REF;
    }

    @Nullable
    private static PsiElement searchInFile(PsiFile file, String key, boolean isTypeRef, String scope) {
        String fname = file.getVirtualFile() != null ? file.getVirtualFile().getName() : file.getName();
        try {
            List<SliccNamedElement> decls = isTypeRef
                    ? collectTypeDeclarations(file)
                    : collectAllDeclarations(file);
            System.err.println("[SLICC-GOTO] key='" + key + "' searching " + fname
                    + " (" + scope + "), " + decls.size() + " decls");
            for (SliccNamedElement decl : decls) {
                String name;
                try {
                    name = decl.getName();
                } catch (ProcessCanceledException e) {
                    throw e;
                } catch (Exception e) {
                    continue;
                }
                if (key.equals(name)) {
                    System.err.println("[SLICC-GOTO] key='" + key + "' FOUND in " + scope
                            + ": " + decl.getClass().getSimpleName() + " in " + fname);
                    PsiElement nameId = decl.getNameIdentifier();
                    return nameId != null ? nameId : decl;
                }
            }
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[SLICC-GOTO] exception in " + fname + ": " + e);
        }
        return null;
    }

    private static List<SliccNamedElement> collectTypeDeclarations(PsiFile file) {
        List<SliccNamedElement> result = new ArrayList<>();
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccStructureDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccExternalTypeDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccEnumerationDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccStateDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccMachineDecl.class));
        return result;
    }

    private static List<SliccNamedElement> collectAllDeclarations(PsiFile file) {
        List<SliccNamedElement> result = new ArrayList<>();
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccStructureDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccExternalTypeDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccEnumerationDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccStateDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccMachineDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccActionDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccFunctionDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccInPortDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccOutPortDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccFieldDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccEnumMember.class));
        return result;
    }
}
