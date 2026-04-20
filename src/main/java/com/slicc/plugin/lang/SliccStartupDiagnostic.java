package com.slicc.plugin.lang;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Startup diagnostic: prints whether .sm files are mapped to SLICC language.
 * Check idea.log for [SLICC-DIAG] entries after project opens.
 */
public class SliccStartupDiagnostic implements ProjectActivity {
    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        com.intellij.openapi.fileTypes.FileType smType =
                FileTypeManager.getInstance().getFileTypeByExtension("sm");
        com.intellij.openapi.fileTypes.FileType sliccType =
                FileTypeManager.getInstance().getFileTypeByExtension("slicc");
        System.err.println("[SLICC-DIAG] .sm   -> " + smType.getName() + " / " + smType.getClass().getName());
        System.err.println("[SLICC-DIAG] .slicc -> " + sliccType.getName() + " / " + sliccType.getClass().getName());
        System.err.println("[SLICC-DIAG] SLICC FileType registered: " + (smType == SliccFileType.INSTANCE));
        return null;
    }
}
