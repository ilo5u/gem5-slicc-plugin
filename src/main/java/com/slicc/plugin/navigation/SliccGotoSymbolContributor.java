package com.slicc.plugin.navigation;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.lang.SliccFileType;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SliccGotoSymbolContributor implements ChooseByNameContributor {

    @NotNull
    @Override
    public String[] getNames(Project project, boolean includeNonProjectItems) {
        List<String> names = new ArrayList<>();
        collectNames(project, names);
        return names.toArray(String[]::new);
    }

    @NotNull
    @Override
    public NavigationItem[] getItemsByName(String name, String pattern,
                                           Project project, boolean includeNonProjectItems) {
        List<NavigationItem> items = new ArrayList<>();
        var scope = GlobalSearchScope.allScope(project);
        var manager = PsiManager.getInstance(project);

        for (var vf : FileTypeIndex.getFiles(SliccFileType.INSTANCE, scope)) {
            var psiFile = manager.findFile(vf);
            if (psiFile == null) continue;
            collectItems(psiFile, name, items);
        }
        return items.toArray(NavigationItem[]::new);
    }

    // ...............................................................................................................................................................................................................

    private void collectNames(Project project, List<String> names) {
        var scope = GlobalSearchScope.allScope(project);
        var manager = PsiManager.getInstance(project);

        for (var vf : FileTypeIndex.getFiles(SliccFileType.INSTANCE, scope)) {
            var psiFile = manager.findFile(vf);
            if (psiFile == null) continue;

            addNames(PsiTreeUtil.collectElementsOfType(psiFile, SliccMachineDecl.class), names);
            addNames(PsiTreeUtil.collectElementsOfType(psiFile, SliccStructureDecl.class), names);
            addNames(PsiTreeUtil.collectElementsOfType(psiFile, SliccEnumerationDecl.class), names);
            addNames(PsiTreeUtil.collectElementsOfType(psiFile, SliccStateDecl.class), names);
            addNames(PsiTreeUtil.collectElementsOfType(psiFile, SliccExternalTypeDecl.class), names);
            addNames(PsiTreeUtil.collectElementsOfType(psiFile, SliccActionDecl.class), names);
            addNames(PsiTreeUtil.collectElementsOfType(psiFile, SliccFunctionDecl.class), names);
        }
    }

    private void addNames(java.util.Collection<? extends SliccNamedElement> elements,
                          List<String> names) {
        for (SliccNamedElement e : elements) {
            String n = e.getName();
            if (n != null && !names.contains(n)) names.add(n);
        }
    }

    private void collectItems(com.intellij.psi.PsiFile psiFile,
                              String name,
                              List<NavigationItem> items) {
        for (SliccNamedElement e : PsiTreeUtil.collectElementsOfType(psiFile, SliccNamedElement.class)) {
            if (name.equals(e.getName())) items.add(e);
        }
    }
}
