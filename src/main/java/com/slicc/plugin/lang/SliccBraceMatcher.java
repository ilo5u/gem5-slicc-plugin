package com.slicc.plugin.lang;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.slicc.plugin.lexer.SliccTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SliccBraceMatcher implements PairedBraceMatcher {

    private static final BracePair[] PAIRS = {
            new BracePair(SliccTokenTypes.OP_LBRACE,   SliccTokenTypes.OP_RBRACE,   true),
            new BracePair(SliccTokenTypes.OP_LPAREN,   SliccTokenTypes.OP_RPAREN,   false),
            new BracePair(SliccTokenTypes.OP_LBRACKET, SliccTokenTypes.OP_RBRACKET, false),
    };

    @NotNull  @Override public BracePair[] getPairs() { return PAIRS; }
    @Override public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType,
                                                              @Nullable IElementType contextType) { return true; }
    @Override public int getCodeConstructStart(PsiFile file, int openingBraceOffset) { return openingBraceOffset; }
}
