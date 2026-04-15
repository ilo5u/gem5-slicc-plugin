package com.slicc.plugin.navigation;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.TokenSet;
import com.slicc.plugin.lexer.SliccLexer;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SliccFindUsagesProvider implements FindUsagesProvider {

    @Nullable
    @Override
    public WordsScanner getWordsScanner() {
        return new DefaultWordsScanner(
                new SliccLexer(),
                TokenSet.create(SliccTokenTypes.IDENTIFIER),
                TokenSet.create(SliccTokenTypes.LINE_COMMENT, SliccTokenTypes.BLOCK_COMMENT),
                TokenSet.create(SliccTokenTypes.STRING_LITERAL)
        );
    }

    @Override
    public boolean canFindUsagesFor(@NotNull PsiElement element) {
        return element instanceof SliccNamedElement;
    }

    @Nullable
    @Override
    @NonNls
    public String getHelpId(@NotNull PsiElement element) {
        return null;
    }

    @NotNull
    @Override
    @Nls
    public String getType(@NotNull PsiElement element) {
        if (element instanceof SliccMachineDecl)     return "machine";
        if (element instanceof SliccStructureDecl)   return "structure";
        if (element instanceof SliccEnumerationDecl) return "enumeration";
        if (element instanceof SliccStateDecl)       return "state_declaration";
        if (element instanceof SliccExternalTypeDecl)return "external_type";
        if (element instanceof SliccActionDecl)      return "action";
        if (element instanceof SliccTransitionDecl)  return "transition";
        if (element instanceof SliccFunctionDecl)    return "function";
        if (element instanceof SliccInPortDecl)      return "in_port";
        if (element instanceof SliccOutPortDecl)     return "out_port";
        if (element instanceof SliccFieldDecl)       return "field";
        if (element instanceof SliccEnumMember)      return "enum member";
        return "identifier";
    }

    @NotNull
    @Override
    @Nls
    public String getDescriptiveName(@NotNull PsiElement element) {
        if (element instanceof PsiNamedElement named) {
            String name = named.getName();
            return name != null ? name : "<unnamed>";
        }
        return element.getText();
    }

    @NotNull
    @Override
    @Nls
    public String getNodeText(@NotNull PsiElement element, boolean useFullName) {
        return getDescriptiveName(element);
    }
}
