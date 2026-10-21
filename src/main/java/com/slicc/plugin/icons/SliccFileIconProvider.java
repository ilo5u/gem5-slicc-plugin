package com.slicc.plugin.icons;

import com.intellij.ide.FileIconProvider;
import com.intellij.ide.IconProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.slicc.plugin.lang.SliccFileType;
import com.slicc.plugin.psi.SliccFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class SliccFileIconProvider extends IconProvider implements FileIconProvider, DumbAware {
    @Override
    public @Nullable Icon getIcon(@NotNull VirtualFile file, int flags, @Nullable Project project) {
        if (file.isDirectory() || file.getFileType() != SliccFileType.INSTANCE) return null;
        return iconForName(file.getName());
    }

    @Override
    public @Nullable Icon getIcon(@NotNull PsiElement element, int flags) {
        if (!(element instanceof SliccFile file)) return null;
        return iconForName(file.getName());
    }

    private static @NotNull Icon iconForName(@NotNull String name) {
        return name.regionMatches(true, name.length() - 6, ".slicc", 0, 6)
                ? SliccIcons.SLICC_FILE : SliccIcons.SM_FILE;
    }
}
