package com.slicc.plugin.lang;

import com.intellij.lang.Language;

public class SliccLanguage extends Language {
    public static final SliccLanguage INSTANCE = new SliccLanguage();

    private SliccLanguage() {
        super("SLICC");
    }
}
