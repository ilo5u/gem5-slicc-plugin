package com.slicc.plugin.highlighting;

import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import org.jetbrains.annotations.NotNull;

public class SliccSyntaxHighlighterFactory extends SingleLazyInstanceSyntaxHighlighterFactory {
    @NotNull
    @Override
    protected SyntaxHighlighter createHighlighter() {
        return new SliccSyntaxHighlighter();
    }
}
