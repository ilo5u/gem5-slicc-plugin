package com.slicc.plugin.navigation;

import com.intellij.ide.structureView.*;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.icons.SliccIcons;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SliccStructureViewFactory implements PsiStructureViewFactory {

    @Override
    public @Nullable StructureViewBuilder getStructureViewBuilder(@NotNull PsiFile psiFile) {
        return new TreeBasedStructureViewBuilder() {
            @NotNull
            @Override
            public StructureViewModel createStructureViewModel(@Nullable Editor editor) {
                return new SliccStructureViewModel(psiFile, editor);
            }
        };
    }

    // ...... View model ........................................................................................................................................................................

    private static class SliccStructureViewModel
            extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider {

        SliccStructureViewModel(PsiFile file, Editor editor) {
            super(file, editor, new SliccFileTreeElement(file));
        }

        @Override public boolean isAlwaysShowsPlus(StructureViewTreeElement e)  { return false; }
        @Override public boolean isAlwaysLeaf(StructureViewTreeElement e)       { return false; }
    }

    // ...... File root element ...................................................................................................................................................

    private static class SliccFileTreeElement extends PsiTreeElementBase<PsiFile> {
        SliccFileTreeElement(PsiFile file) { super(file); }

        @Override public @Nullable String getPresentableText() {
            PsiFile f = getElement();
            return f != null ? f.getName() : "";
        }

        @NotNull
        @Override
        public Collection<StructureViewTreeElement> getChildrenBase() {
            List<StructureViewTreeElement> result = new ArrayList<>();
            PsiFile file = getElement();
            if (file == null) return result;

            for (SliccMachineDecl m : PsiTreeUtil.collectElementsOfType(file, SliccMachineDecl.class)) {
                result.add(new SliccMachineTreeElement(m));
            }
            for (SliccStructureDecl s : PsiTreeUtil.collectElementsOfType(file, SliccStructureDecl.class)) {
                result.add(new NamedTreeElement<>(s));
            }
            for (SliccEnumerationDecl e : PsiTreeUtil.collectElementsOfType(file, SliccEnumerationDecl.class)) {
                result.add(new NamedTreeElement<>(e));
            }
            for (SliccExternalTypeDecl e : PsiTreeUtil.collectElementsOfType(file, SliccExternalTypeDecl.class)) {
                result.add(new NamedTreeElement<>(e));
            }
            for (SliccFunctionDecl f : PsiTreeUtil.collectElementsOfType(file, SliccFunctionDecl.class)) {
                // only top-level functions (not inside machine)
                if (f.getParent() instanceof SliccFile) {
                    result.add(new NamedTreeElement<>(f));
                }
            }
            return result;
        }
    }

    // ...... Machine element (has children: actions, transitions, functions, ports)
    private static class SliccMachineTreeElement extends PsiTreeElementBase<SliccMachineDecl> {
        SliccMachineTreeElement(SliccMachineDecl m) { super(m); }

        @Override public @Nullable String getPresentableText() {
            SliccMachineDecl m = getElement();
            return m != null ? "machine(" + m.getName() + ")" : "";
        }

        @Override public @Nullable Icon getIcon(boolean open) { return SliccIcons.MACHINE; }

        @NotNull @Override
        public Collection<StructureViewTreeElement> getChildrenBase() {
            List<StructureViewTreeElement> result = new ArrayList<>();
            SliccMachineDecl m = getElement();
            if (m == null) return result;

            for (SliccInPortDecl p : PsiTreeUtil.collectElementsOfType(m, SliccInPortDecl.class)) {
                result.add(new NamedTreeElement<>(p));
            }
            for (SliccOutPortDecl p : PsiTreeUtil.collectElementsOfType(m, SliccOutPortDecl.class)) {
                result.add(new NamedTreeElement<>(p));
            }
            for (SliccActionDecl a : PsiTreeUtil.collectElementsOfType(m, SliccActionDecl.class)) {
                result.add(new NamedTreeElement<>(a));
            }
            for (SliccTransitionDecl t : PsiTreeUtil.collectElementsOfType(m, SliccTransitionDecl.class)) {
                result.add(new NamedTreeElement<>(t));
            }
            for (SliccFunctionDecl f : PsiTreeUtil.collectElementsOfType(m, SliccFunctionDecl.class)) {
                result.add(new NamedTreeElement<>(f));
            }
            return result;
        }
    }

    // ...... Generic leaf element for any named declaration ...............................................................

    private static class NamedTreeElement<T extends SliccNamedElement>
            extends PsiTreeElementBase<T> {

        NamedTreeElement(T element) { super(element); }

        @Override public @Nullable String getPresentableText() {
            T e = getElement();
            return e != null ? e.getName() : "";
        }

        @Override public @Nullable Icon getIcon(boolean open) {
            T e = getElement();
            if (e == null) return null;
            ItemPresentation p = e.getPresentation();
            return p != null ? p.getIcon(open) : null;
        }

        @NotNull @Override
        public Collection<StructureViewTreeElement> getChildrenBase() {
            return List.of(); // leaf
        }
    }
}
