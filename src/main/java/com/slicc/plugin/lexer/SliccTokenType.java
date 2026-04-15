package com.slicc.plugin.lexer;

import com.intellij.psi.tree.IElementType;
import com.slicc.plugin.lang.SliccLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SliccTokenType extends IElementType {
    public SliccTokenType(@NotNull @NonNls String debugName) {
        super(debugName, SliccLanguage.INSTANCE);
    }

    @Override
    public String toString() {
        return "SliccTokenType." + super.toString();
    }
}
