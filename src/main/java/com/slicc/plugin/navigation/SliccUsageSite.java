package com.slicc.plugin.navigation;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.icons.SliccIcons;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class SliccUsageSite extends FakePsiElement {

    public static final String MAX_STATEMENT_LENGTH_PROPERTY = "slicc.navigation.maxStatementLength";
    public static final int DEFAULT_MAX_STATEMENT_LENGTH = 160;

    private final SmartPsiElementPointer<PsiElement> reference;
    private final PsiManager manager;
    private final Icon icon;

    public SliccUsageSite(@NotNull PsiElement call) {
        this(call, SliccIcons.FUNCTION);
    }

    public SliccUsageSite(@NotNull PsiElement call, @NotNull Icon icon) {
        reference = SmartPointerManager.getInstance(call.getProject()).createSmartPsiElementPointer(call);
        manager = call.getManager();
        this.icon = icon;
    }

    static PsiElement statementOf(PsiElement call) {
        for (PsiElement element = call.getParent(); element != null && !(element instanceof PsiFile);
             element = element.getParent()) {
            if (element instanceof SliccFunctionDecl || element instanceof SliccActionDecl) break;
            if (element instanceof SliccFieldDecl || element instanceof SliccEnumMember
                    || element instanceof SliccTransitionDecl || element instanceof SliccInPortDecl
                    || element instanceof SliccOutPortDecl || element instanceof SliccMachineDecl
                    || element.getNode().getElementType() == SliccTypes.STATEMENT) {
                return element;
            }
        }
        return call;
    }

    @Override
    public @NotNull String getName() {
        PsiElement call = reference.getElement();
        if (call == null) return "<invalid usage>";
        StringBuilder text = new StringBuilder();
        PsiTreeUtil.processElements(statementOf(call), element -> {
            if (element.getFirstChild() != null) return true;
            if (element instanceof PsiWhiteSpace) {
                if (!text.isEmpty() && text.charAt(text.length() - 1) != ' ') text.append(' ');
            } else {
                text.append(element.getText().replaceAll("[\\r\\n]+", " "));
            }
            return true;
        });
        String statement = text.toString().trim();
        int limit = Math.max(1, Integer.getInteger(MAX_STATEMENT_LENGTH_PROPERTY, DEFAULT_MAX_STATEMENT_LENGTH));
        return statement.codePointCount(0, statement.length()) <= limit ? statement
                : statement.substring(0, statement.offsetByCodePoints(0, limit - 1)) + "…";
    }

    @Override
    public @Nullable String getLocationString() {
        PsiElement call = reference.getElement();
        PsiFile file = getContainingFile();
        if (call == null || file == null) return null;
        Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
        String location = file.getName();
        if (document != null) location += ":" + (document.getLineNumber(call.getTextOffset()) + 1);
        SliccNamedElement caller = PsiTreeUtil.getParentOfType(call, SliccFunctionDecl.class, SliccActionDecl.class,
                SliccInPortDecl.class, SliccTransitionDecl.class);
        return caller == null ? location : caller.getName() + " — " + location;
    }

    @Override
    public Icon getIcon(boolean unused) {
        return icon;
    }

    @Override
    public ItemPresentation getPresentation() {
        return this;
    }

    @Override
    public @Nullable PsiElement getParent() {
        PsiElement call = reference.getElement();
        return call == null ? null : call.getParent();
    }

    @Override
    public @Nullable PsiFile getContainingFile() {
        return reference.getContainingFile();
    }

    @Override
    public @NotNull PsiManager getManager() {
        return manager;
    }

    @Override
    public @Nullable TextRange getTextRange() {
        PsiElement call = reference.getElement();
        return call == null ? null : statementOf(call).getTextRange();
    }

    @Override
    public int getTextOffset() {
        PsiElement call = reference.getElement();
        return call == null ? 0 : call.getTextOffset();
    }

    @Override
    public @Nullable String getText() {
        PsiElement call = reference.getElement();
        return call == null ? null : statementOf(call).getText();
    }

    @Override
    public int getTextLength() {
        String text = getText();
        return text == null ? 0 : text.length();
    }

    @Override
    public boolean isValid() {
        PsiElement call = reference.getElement();
        return call != null && call.isValid();
    }

    @Override
    public boolean canNavigate() {
        PsiFile file = getContainingFile();
        return isValid() && file != null && file.getVirtualFile() != null;
    }

    @Override
    public boolean canNavigateToSource() {
        return canNavigate();
    }

    @Override
    public void navigate(boolean requestFocus) {
        PsiFile file = getContainingFile();
        if (canNavigate() && file != null) {
            new OpenFileDescriptor(getProject(), file.getVirtualFile(), getTextOffset()).navigate(requestFocus);
        }
    }
}
