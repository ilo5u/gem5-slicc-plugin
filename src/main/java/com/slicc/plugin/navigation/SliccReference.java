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
    // Whether this reference is on a type-name position (inside TYPE_REF node)
    private final boolean myIsTypeRef;

    public SliccReference(@NotNull PsiElement element) {
        super(element, new TextRange(0, element.getTextLength()));
        myKey = element.getText();
        // Determine if this IDENTIFIER is inside a TYPE_REF node
        boolean isTypeRef = false;
        if (element.getParent() instanceof SliccCompositeElement p
                && p.getNode().getElementType() == SliccTypes.TYPE_REF) {
            isTypeRef = true;
        }
        myIsTypeRef = isTypeRef;
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

        try {
            // 1. Protocol scope
            Set<PsiFile> protocolGroup = SliccProtocolIndex.groupContaining(file);
            PsiElement result = searchInFiles(protocolGroup, "protocol");
            if (result != null) return result;

            // 2. Global scope
            Set<PsiFile> globalFiles = SliccProtocolIndex.globalFiles(project);
            System.err.println("[SLICC-RESOLVE] key='" + myKey + "' trying globalFiles size=" + globalFiles.size());
            result = searchInFiles(globalFiles, "global");
            if (result == null) {
                System.err.println("[SLICC-RESOLVE] key='" + myKey + "' NOT FOUND anywhere");
            }
            return result;
        } catch (Exception e) {
            System.err.println("[SLICC-RESOLVE] key='" + myKey + "' EXCEPTION: " + e);
            e.printStackTrace(System.err);
            return null;
        }
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        PsiFile file = myElement.getContainingFile();
        if (file == null) return PsiReference.EMPTY_ARRAY;
        var project = file.getProject();
        List<String> variants = new ArrayList<>();
        try {
            for (PsiFile f : SliccProtocolIndex.groupContaining(file))
                addVariants(f, variants);
            for (PsiFile f : SliccProtocolIndex.globalFiles(project))
                addVariants(f, variants);
        } catch (Exception e) {
            System.err.println("[SLICC-RESOLVE] getVariants exception: " + e);
        }
        return variants.toArray();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    @Nullable
    private PsiElement searchInFiles(Set<PsiFile> files, String scope) {
        for (PsiFile f : files) {
            String fname = "<unknown>";
            try {
                fname = f.getVirtualFile() != null ? f.getVirtualFile().getName() : f.getName();
                // Choose declaration list based on context:
                // TYPE_REF positions → search type declarations first, then others
                // Expression positions → search all declarations
                List<SliccNamedElement> decls = myIsTypeRef
                        ? collectTypeDeclarations(f)
                        : collectAllDeclarations(f);
                for (SliccNamedElement decl : decls) {
                    String name = null;
                    try { name = decl.getName(); } catch (Exception e) {
                        System.err.println("[SLICC-RESOLVE] getName() exception in " + fname
                                + " on " + decl.getClass().getSimpleName() + ": " + e);
                    }
                    if (myKey.equals(name)) {
                        System.err.println("[SLICC-RESOLVE] key='" + myKey + "' FOUND in "
                                + scope + " scope: " + decl.getClass().getSimpleName()
                                + " in " + fname);
                        return decl;
                    }
                }
            } catch (Exception e) {
                System.err.println("[SLICC-RESOLVE] key='" + myKey + "' exception in file "
                        + fname + ": " + e);
            }
        }
        return null;
    }

    private void addVariants(PsiFile file, List<String> out) {
        try {
            for (SliccNamedElement decl : collectAllDeclarations(file)) {
                try {
                    String name = decl.getName();
                    if (name != null && !name.isEmpty() && !out.contains(name))
                        out.add(name);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    /**
     * For TYPE_REF positions: only search type-level declarations.
     * Excludes field members and enum members to avoid false matches.
     */
    private static List<SliccNamedElement> collectTypeDeclarations(PsiFile file) {
        List<SliccNamedElement> result = new ArrayList<>();
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccStructureDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccExternalTypeDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccEnumerationDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccStateDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccMachineDecl.class));
        return result;
    }

    /**
     * For expression/statement positions: search all declarations.
     */
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