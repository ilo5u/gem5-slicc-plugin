package com.slicc.plugin.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.slicc.plugin.lang.SliccFileType;
import com.slicc.plugin.lang.SliccLanguage;
import org.jetbrains.annotations.NotNull;

public class SliccFile extends PsiFileBase {
    public SliccFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, SliccLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public FileType getFileType() {
        return SliccFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "SLICC File: " + getName();
    }
}
