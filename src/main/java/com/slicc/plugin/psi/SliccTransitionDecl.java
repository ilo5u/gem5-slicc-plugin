package com.slicc.plugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.icons.SliccIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class SliccTransitionDecl extends SliccNamedElement {

    public SliccTransitionDecl(@NotNull ASTNode node) { super(node); }

    @Override protected Icon getIcon() { return SliccIcons.TRANSITION; }

    @Override
    @Nullable
    public String getName() {
        ASTNode states = getNode().findChildByType(SliccTypes.TRANSITION_STATES);
        ASTNode events = getNode().findChildByType(SliccTypes.TRANSITION_EVENTS);
        String s = states != null ? compact(states.getText()) : "...";
        String e = events != null ? compact(events.getText()) : "...";
        return s + " x " + e;
    }

    @Override
    @Nullable
    public PsiElement getNameIdentifier() { return null; }

    @Override
    public ItemPresentation getPresentation() {
        ASTNode closing = getNode().findChildByType(SliccTokenTypes.OP_RPAREN);
        String label = closing != null
                ? getText().substring(0, closing.getStartOffset() + closing.getTextLength() - getTextOffset())
                        .replaceAll("\\s+", " ").trim()
                : "transition(" + getName() + ")";
        PsiFile file = getContainingFile();
        String location = file != null ? file.getName() : null;
        return new ItemPresentation() {
            @Override public @NotNull String getPresentableText() { return label; }
            @Override public @Nullable String getLocationString() { return location; }
            @Override public @Nullable Icon getIcon(boolean unused) { return SliccIcons.TRANSITION; }
        };
    }

    private static String compact(String text) {
        return text.replaceAll("\\s+", "").trim();
    }
}
