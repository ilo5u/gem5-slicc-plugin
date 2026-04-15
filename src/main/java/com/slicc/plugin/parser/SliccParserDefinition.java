package com.slicc.plugin.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.slicc.plugin.lang.SliccLanguage;
import com.slicc.plugin.lexer.SliccLexer;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.psi.SliccFile;
import org.jetbrains.annotations.NotNull;

public class SliccParserDefinition implements ParserDefinition {

    public static final IFileElementType FILE =
            new IFileElementType(SliccLanguage.INSTANCE);

    public static final TokenSet COMMENTS = TokenSet.create(
            SliccTokenTypes.LINE_COMMENT,
            SliccTokenTypes.BLOCK_COMMENT
    );

    public static final TokenSet STRINGS = TokenSet.create(
            SliccTokenTypes.STRING_LITERAL
    );

    public static final TokenSet WHITESPACES = TokenSet.create(
            SliccTokenTypes.WHITE_SPACE
    );

    @NotNull
    @Override
    public Lexer createLexer(Project project) {
        return new SliccLexer();
    }

    @NotNull
    @Override
    public PsiParser createParser(Project project) {
        return new SliccParser();
    }

    @NotNull
    @Override
    public IFileElementType getFileNodeType() {
        return FILE;
    }

    @NotNull
    @Override
    public TokenSet getCommentTokens() {
        return COMMENTS;
    }

    @NotNull
    @Override
    public TokenSet getStringLiteralElements() {
        return STRINGS;
    }

    @NotNull
    @Override
    public PsiElement createElement(ASTNode node) {
        return SliccPsiElementFactory.createElement(node);
    }

    @NotNull
    @Override
    public PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new SliccFile(viewProvider);
    }

    @NotNull
    @Override
    public SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
        return SpaceRequirements.MAY;
    }
}
