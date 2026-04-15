package com.slicc.plugin.navigation;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a plain IDENTIFIER token to the named SLICC declaration that defines it.
 *
 * Resolution order (all within the same file for now):
 *   1. action declarations
 *   2. function declarations
 *   3. structure / external_type declarations
 *   4. enumeration / state_declaration declarations
 *   5. machine declarations
 *   6. in_port / out_port declarations
 *   7. field declarations inside the enclosing structure
 *   8. enum member declarations
 *
 * Cross-file resolution (via include directives) is a future enhancement;
 * the infrastructure is in place to extend getVariants() for that.
 */
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
        if (file == null) return null;

        // Walk through all named declarations in the file
        for (SliccNamedElement decl : collectAllDeclarations(file)) {
            if (myKey.equals(decl.getName())) return decl;
        }
        return null;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        PsiFile file = myElement.getContainingFile();
        if (file == null) return PsiReference.EMPTY_ARRAY;
        List<Object> variants = new ArrayList<>();
        for (SliccNamedElement decl : collectAllDeclarations(file)) {
            String name = decl.getName();
            if (name != null && !name.isEmpty()) variants.add(name);
        }
        return variants.toArray();
    }

    // ...... helpers ..............................................................................................................................................................................

    private static List<SliccNamedElement> collectAllDeclarations(PsiFile file) {
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
