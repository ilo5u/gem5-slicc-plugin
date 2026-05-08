package com.slicc.plugin.lang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.slicc.plugin.icons.SliccIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SliccFileType extends LanguageFileType {
    public static final SliccFileType INSTANCE = new SliccFileType();

    private SliccFileType() {
        super(SliccLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return "SLICC File";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "SLICC state machine specification file (gem5 Ruby)";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "sm";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return SliccIcons.FILE;
    }
}
