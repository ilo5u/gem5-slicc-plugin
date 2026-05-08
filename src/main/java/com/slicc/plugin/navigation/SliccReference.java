package com.slicc.plugin.navigation;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.progress.ProcessCanceledException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SliccReference extends PsiReferenceBase<PsiElement> {

    private static final Key<CachedValue<Map<String, SliccNamedElement>>> ALL_DECL_MAP_KEY =
            Key.create("SLICC_ALL_DECL_MAP");
    private static final Key<CachedValue<Map<String, SliccNamedElement>>> TYPE_DECL_MAP_KEY =
            Key.create("SLICC_TYPE_DECL_MAP");

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
        if (file == null) return null;

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
        } catch (Exception ignored) {}

        // 3. Global scope
        try {
            Set<PsiFile> globalFiles = SliccProtocolIndex.globalFiles(file.getProject());
            for (PsiFile f : globalFiles) {
                result = searchInFile(f, "global");
                if (result != null) return result;
            }
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * Override to avoid relying solely on PSI identity. The default implementation calls
     * areElementsEquivalent(resolve(), element) which uses ==. That fails when the cached
     * map holds a different instance than the target (e.g. after a reparse, or for overloads
     * where putIfAbsent stored the first overload). We fall back to name + kind matching.
     */
    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
        if (!(element instanceof SliccNamedElement named)) return false;
        String targetName = named.getName();
        if (targetName == null || !targetName.equals(myKey)) return false;
        PsiElement resolved = resolve();
        if (resolved == null) return false;
        if (PsiManager.getInstance(element.getProject()).areElementsEquivalent(resolved, element)) return true;
        // Fallback: same name + same declaration kind (handles overloads and PSI identity mismatches)
        return resolved.getClass() == element.getClass();
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
        try {
            Map<String, SliccNamedElement> map = myIsTypeRef ? getTypeDeclMap(f) : getAllDeclMap(f);
            SliccNamedElement decl = map.get(myKey);
            if (decl != null) {
                return decl; // return the declaration so Find Usages identity check works
            }
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Resolves to a specific target declaration (used by Find Usages to check
     * whether this reference points to a given element).
     * Returns the name identifier of the first declaration with the same name,
     * which is sufficient for non-overloaded symbols. For overloaded functions
     * the GotoDeclarationHandler handles precise overload selection.
     */
    @Nullable
    public PsiElement resolveToTarget(@NotNull PsiElement target) {
        PsiFile file = myElement.getContainingFile();
        if (file == null) return null;
        // If the target's name matches, resolve normally
        if (target instanceof SliccNamedElement named) {
            String targetName = named.getName();
            if (targetName != null && targetName.equals(myKey)) {
                return resolve();
            }
        }
        return null;
    }

    private static Map<String, SliccNamedElement> getAllDeclMap(PsiFile file) {
        return CachedValuesManager.getCachedValue(file, ALL_DECL_MAP_KEY, () ->
                CachedValueProvider.Result.create(buildDeclMap(file, false), file));
    }

    private static Map<String, SliccNamedElement> getTypeDeclMap(PsiFile file) {
        return CachedValuesManager.getCachedValue(file, TYPE_DECL_MAP_KEY, () ->
                CachedValueProvider.Result.create(buildDeclMap(file, true), file));
    }

    private static Map<String, SliccNamedElement> buildDeclMap(PsiFile file, boolean typeOnly) {
        Map<String, SliccNamedElement> map = new java.util.LinkedHashMap<>();
        List<SliccNamedElement> decls = typeOnly ? collectTypeDeclarations(file) : collectAllDeclarations(file);
        for (SliccNamedElement decl : decls) {
            try {
                String name = decl.getName();
                if (name != null) map.putIfAbsent(name, decl);
            } catch (Exception ignored) {}
        }
        return map;
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