package com.slicc.plugin.completion;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.hint.ShowParameterInfoHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.slicc.plugin.lang.SliccFileType;
import org.jetbrains.annotations.NotNull;

/**
 * Triggers the parameter info popup automatically when the user types '(' in a SLICC file.
 */
public class SliccTypedHandler extends TypedHandlerDelegate {

    @Override
    @NotNull
    public Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        if (c == '(' && file.getFileType() == SliccFileType.INSTANCE) {
            int offset = editor.getCaretModel().getOffset();
            System.err.println("[SLICC-TH] charTyped '(' offset=" + offset + " file=" + file.getName());
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!editor.isDisposed()) {
                    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
                    System.err.println("[SLICC-TH] invoking ShowParameterInfoHandler at offset=" + offset);
                    ShowParameterInfoHandler.invoke(project, editor, file, offset, null, false);
                }
            });
        }
        return Result.CONTINUE;
    }
}
