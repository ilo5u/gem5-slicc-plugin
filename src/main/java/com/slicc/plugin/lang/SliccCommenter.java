package com.slicc.plugin.lang;

import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.psi.PsiComment;
import com.intellij.psi.tree.IElementType;
import com.slicc.plugin.lexer.SliccTokenTypes;
import org.jetbrains.annotations.Nullable;

public class SliccCommenter implements CodeDocumentationAwareCommenter {

    @Nullable @Override public String getLineCommentPrefix()          { return "// "; }
    @Nullable @Override public String getBlockCommentPrefix()         { return "/* "; }
    @Nullable @Override public String getBlockCommentSuffix()         { return " */"; }
    @Nullable @Override public String getCommentedBlockCommentPrefix() { return null; }
    @Nullable @Override public String getCommentedBlockCommentSuffix() { return null; }

    @Nullable @Override public IElementType getLineCommentTokenType()      { return SliccTokenTypes.LINE_COMMENT; }
    @Nullable @Override public IElementType getBlockCommentTokenType()     { return SliccTokenTypes.BLOCK_COMMENT; }
    @Nullable @Override public IElementType getDocumentationCommentTokenType() { return null; }
    @Nullable @Override public String getDocumentationCommentPrefix()     { return null; }
    @Nullable @Override public String getDocumentationCommentLinePrefix() { return null; }
    @Nullable @Override public String getDocumentationCommentSuffix()     { return null; }

    @Override public boolean isDocumentationComment(PsiComment element) { return false; }
}
