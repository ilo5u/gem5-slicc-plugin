package com.slicc.plugin.navigation;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.progress.ProcessCanceledException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SliccReference extends PsiReferenceBase<PsiElement> {

    private final String myKey;
    private final boolean myIsTypeRef;

    public SliccReference(@NotNull PsiElement element) {
        super(element, new TextRange(0, element.getTextLength()));
        myKey = element.getText();
        myIsTypeRef = isInTypeRefPosition(element);
    }

    /** Returns true if this identifier is used as a type name (inside a TYPE_REF node). */
    private static boolean isInTypeRefPosition(@NotNull PsiElement element) {
        PsiElement parent = element.getParent();
        return parent instanceof SliccCompositeElement p
                && p.getNode().getElementType() == SliccTypes.TYPE_REF;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        PsiFile file = myElement.getContainingFile();
        if (file == null) {
            System.err.println("[SLICC-RESOLVE] key='" + myKey + "' getContainingFile() is null");
            return null;
        }
        System.err.println("[SLICC-RESOLVE] resolving key='" + myKey + "' isTypeRef=" + myIsTypeRef
                + " in " + file.getName());

        // 1. Always search the containing file first — no index required.
        PsiElement result = searchInFile(file, "self");
        if (result != null) return result;

        // 2. Protocol scope
        try {
            Set<PsiFile> protocolGroup = SliccProtocolIndex.groupContaining(file);
            for (PsiFile f : protocolGroup) {
                result = searchInFile(f, "protocol");
                if (result != null) return result;
            }
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[SLICC-RESOLVE] key='" + myKey + "' group lookup error: " + e);
        }

        // 3. Global scope
        try {
            Set<PsiFile> globalFiles = SliccProtocolIndex.globalFiles(file.getProject());
            for (PsiFile f : globalFiles) {
                result = searchInFile(f, "global");
                if (result != null) return result;
            }
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[SLICC-RESOLVE] key='" + myKey + "' global lookup error: " + e);
        }
        System.err.println("[SLICC-RESOLVE] key='" + myKey + "' NOT FOUND in any scope");
        return null;
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
    private PsiElement searchInFile(PsiFile f, String scope) {
        String fname = f.getVirtualFile() != null ? f.getVirtualFile().getName() : f.getName();
        try {
            List<SliccNamedElement> decls = myIsTypeRef
                    ? collectTypeDeclarations(f)
                    : collectAllDeclarations(f);
            System.err.println("[SLICC-RESOLVE] key='" + myKey + "' searching " + fname
                    + " (" + scope + "), found " + decls.size() + " decls");
            for (SliccNamedElement decl : decls) {
                String name;
                try {
                    name = decl.getName();
                } catch (ProcessCanceledException e) {
                    throw e;
                } catch (Exception e) {
                    System.err.println("[SLICC-RESOLVE] getName() exception on "
                            + decl.getClass().getSimpleName() + " in " + fname + ": " + e);
                    continue;
                }
                if (myKey.equals(name)) {
                    System.err.println("[SLICC-RESOLVE] key='" + myKey + "' FOUND in "
                            + scope + ": " + decl.getClass().getSimpleName() + " in " + fname);
                    // Return the name identifier for precise navigation; fall back to decl.
                    PsiElement nameId = decl.getNameIdentifier();
                    return nameId != null ? nameId : decl;
                }
            }
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[SLICC-RESOLVE] key='" + myKey
                    + "' exception in file " + fname + ": " + e);
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