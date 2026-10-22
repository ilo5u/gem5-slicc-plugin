package com.slicc.plugin.navigation;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
        return SliccGotoDeclarationHandler.resolveReference(myElement);
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
        if (!(element instanceof SliccNamedElement named)) return false;
        String targetName = named.getName();
        if (targetName == null || !targetName.equals(myKey)
                && !(named instanceof SliccEnumMember member && SliccEnumDefaults.isAlias(myElement, member))
                && !((named instanceof SliccEnumerationDecl || named instanceof SliccStateDecl)
                && named.equals(SliccGeneratedFunctions.resolve(myElement)))) return false;
        PsiElement resolved = resolve();
        if (resolved == null) return false;
        if (resolved instanceof SliccFunctionDecl function && element instanceof SliccFunctionDecl target) {
            return SliccFunctionNavigation.isSameFunction(function, target);
        }
        return PsiManager.getInstance(element.getProject()).areElementsEquivalent(resolved, element);
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        PsiFile file = myElement.getContainingFile();
        if (file == null) return PsiReference.EMPTY_ARRAY;
        var project = file.getProject();
        List<String> variants = new ArrayList<>();
        if (!myIsTypeRef) {
            for (SliccFieldDecl field : SliccGotoDeclarationHandler.collectVisibleVariables(myElement)) {
                String name = field.getName();
                if (name != null && (!SliccMessageScope.isMessageVariable(name)
                        || SliccMessageScope.findParameter(name, myElement) != null) && !variants.contains(name)) {
                    variants.add(name);
                }
            }
        }
        for (String name : List.of("in_msg", "out_msg")) {
            if (SliccMessageScope.isVisible(name, myElement) && !variants.contains(name)) variants.add(name);
        }
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
    public PsiElement resolveToTarget(@NotNull PsiElement target) {
        return isReferenceTo(target) ? resolve() : null;
    }

    private void addVariants(PsiFile file, List<String> out) {
        try {
            for (SliccNamedElement decl : myIsTypeRef ? collectTypeDeclarations(file) : collectAllDeclarations(file)) {
                try {
                    String name = decl.getName();
                    if (name != null && !name.isEmpty() && !SliccMessageScope.isMessageVariable(name)
                            && !out.contains(name))
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
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccEnumMember.class));
        return result;
    }
}
