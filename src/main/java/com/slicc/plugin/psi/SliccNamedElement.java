package com.slicc.plugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.util.IncorrectOperationException;
import com.slicc.plugin.lexer.SliccTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Base class for all named SLICC PSI declarations (machine, structure,
 * enumeration, action, function, etc.).
 *
 * Sub-classes only need to override {@link #getIcon()} and optionally
 * {@link #getName()} / {@link #getNameIdentifier()}.
 */
public abstract class SliccNamedElement extends SliccCompositeElement
        implements PsiNameIdentifierOwner, NavigationItem {

    protected SliccNamedElement(@NotNull ASTNode node) {
        super(node);
    }

    // ...... Name .......................................................................................................................................................................................

    @Nullable
    @Override
    public String getName() {
        PsiElement id = getNameIdentifier();
        return id != null ? id.getText() : null;
    }

    @NotNull
    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        throw new IncorrectOperationException("Rename refactoring is not yet supported for SLICC");
    }

    @Nullable
    @Override
    public PsiElement getNameIdentifier() {
        ASTNode id = getNode().findChildByType(SliccTokenTypes.IDENTIFIER);
        return id != null ? id.getPsi() : null;
    }

    // ...... Icon (override in each sub-class) ................................................................................................

    protected abstract Icon getIcon();

    // ...... NavigationItem presentation ..................................................................................................................

    @Override
    public ItemPresentation getPresentation() {
        final String displayName = getName();
        final Icon   icon        = getIcon();
        return new ItemPresentation() {
            @Override public @Nullable String getPresentableText()    { return displayName; }
            @Override public @Nullable String getLocationString()     { return null; }
            @Override public @Nullable Icon   getIcon(boolean unused) { return icon;  }
        };
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + getName() + ")";
    }
}
