package com.slicc.plugin.psi;

import com.intellij.psi.tree.IElementType;
import com.slicc.plugin.lang.SliccLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SliccElementType extends IElementType {
    public SliccElementType(@NotNull @NonNls String debugName) {
        super(debugName, SliccLanguage.INSTANCE);
    }
}
