package com.slicc.plugin.navigation;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.FakePsiElement;
import com.slicc.plugin.icons.SliccIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class SliccCppTypeTarget extends FakePsiElement {

    private final SmartPsiFileRange pointer;
    private final PsiManager manager;
    private final String qualifiedName;

    SliccCppTypeTarget(PsiFile file, SliccCppTypes.Definition definition) {
        manager = file.getManager();
        qualifiedName = definition.qualifiedName();
        pointer = SmartPointerManager.getInstance(file.getProject()).createSmartPsiFileRangePointer(file,
                TextRange.from(definition.offset(), definition.length()));
    }

    @Override public @NotNull String getName() { return qualifiedName; }
    @Override public @Nullable PsiElement getParent() { return pointer.getElement(); }
    @Override public @Nullable PsiFile getContainingFile() { return pointer.getElement(); }
    @Override public @NotNull PsiManager getManager() { return manager; }
    @Override public @NotNull Icon getIcon(boolean unused) { return SliccIcons.STRUCTURE; }
    @Override public int getTextOffset() { Segment range = pointer.getRange(); return range == null ? 0 : range.getStartOffset(); }
    @Override public @Nullable TextRange getTextRange() { Segment range = pointer.getRange(); return range == null ? null : TextRange.create(range); }
    @Override public boolean isValid() { return pointer.getElement() != null && pointer.getRange() != null; }
    @Override public boolean canNavigate() { return isValid() && pointer.getVirtualFile() != null; }
    @Override public boolean canNavigateToSource() { return canNavigate(); }

    @Override
    public @Nullable String getLocationString() {
        PsiFile file = pointer.getElement();
        if (file == null) return null;
        var document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
        return file.getVirtualFile().getPresentableUrl()
                + (document == null ? "" : ":" + (document.getLineNumber(getTextOffset()) + 1));
    }

    @Override
    public void navigate(boolean requestFocus) {
        if (canNavigate()) new OpenFileDescriptor(getProject(), pointer.getVirtualFile(), getTextOffset()).navigate(requestFocus);
    }
}
