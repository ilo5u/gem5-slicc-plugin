package com.slicc.plugin.navigation;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SliccReference extends PsiReferenceBase<PsiElement> {

    private final String myKey;

    public SliccReference(@NotNull PsiElement element) {
        super(element, new TextRange(0, element.getTextLength()));
        myKey = element.getText();
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        PsiFile file = myElement.getContainingFile();
        if (file == null) {
            System.err.println("[SLICC-RESOLVE] key='" + myKey + "' FAIL: no containing file");
            return null;
        }
        var project = file.getProject();

        // 1. Protocol scope
        Set<PsiFile> protocolGroup = SliccProtocolIndex.groupContaining(file);
        System.err.println("[SLICC-RESOLVE] key='" + myKey + "' protocolGroup size=" + protocolGroup.size()
                + " files=" + protocolGroup.stream()
                .map(f -> f.getVirtualFile() != null ? f.getVirtualFile().getName() : f.getName())
                .toList());
        PsiElement result = searchInFiles(protocolGroup);
        if (result != null) {
            System.err.println("[SLICC-RESOLVE] key='" + myKey + "' FOUND in protocol scope: "
                    + result.getClass().getSimpleName()
                    + " in " + result.getContainingFile().getVirtualFile().getName());
            return result;
        }

        // 2. Global scope
        Set<PsiFile> globalFiles = SliccProtocolIndex.globalFiles(project);
        System.err.println("[SLICC-RESOLVE] key='" + myKey + "' globalFiles size=" + globalFiles.size()
                + " files=" + globalFiles.stream()
                .map(f -> f.getVirtualFile() != null ? f.getVirtualFile().getName() : f.getName())
                .toList());
        result = searchInFiles(globalFiles);
        if (result != null) {
            System.err.println("[SLICC-RESOLVE] key='" + myKey + "' FOUND in global scope: "
                    + result.getClass().getSimpleName()
                    + " in " + result.getContainingFile().getVirtualFile().getName());
        } else {
            System.err.println("[SLICC-RESOLVE] key='" + myKey + "' NOT FOUND anywhere");
        }
        return result;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        PsiFile file = myElement.getContainingFile();
        if (file == null) return PsiReference.EMPTY_ARRAY;
        var project = file.getProject();

        List<String> variants = new ArrayList<>();
        for (PsiFile f : SliccProtocolIndex.groupContaining(file))
            addVariants(f, variants);
        for (PsiFile f : SliccProtocolIndex.globalFiles(project))
            addVariants(f, variants);
        return variants.toArray();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    @Nullable
    private PsiElement searchInFiles(Set<PsiFile> files) {
        for (PsiFile f : files) {
            List<SliccNamedElement> decls = collectDeclarations(f);
            for (SliccNamedElement decl : decls) {
                String name = decl.getName();
                if (myKey.equals(name)) return decl;
            }
        }
        return null;
    }

    private void addVariants(PsiFile file, List<String> out) {
        for (SliccNamedElement decl : collectDeclarations(file)) {
            String name = decl.getName();
            if (name != null && !name.isEmpty() && !out.contains(name))
                out.add(name);
        }
    }

    private static List<SliccNamedElement> collectDeclarations(PsiFile file) {
        List<SliccNamedElement> result = new ArrayList<>();
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccActionDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccFunctionDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccStructureDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccExternalTypeDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccEnumerationDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccStateDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccMachineDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccInPortDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccOutPortDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccFieldDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccEnumMember.class));
        return result;
    }
}