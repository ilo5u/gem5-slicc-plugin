package com.slicc.plugin.navigation;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Populates Go to Symbol (Ctrl+Alt+Shift+N).
 *
 * Shows symbols from both scopes:
 *   - Global scope (RubySlicc_interfaces.slicc and its includes)
 *   - All protocol groups (each *.slicc root and its includes)
 */
public class SliccGotoSymbolContributor implements ChooseByNameContributor {

    @NotNull
    @Override
    public String[] getNames(Project project, boolean includeNonProjectItems) {
        List<String> names = new ArrayList<>();

        // Global scope
        for (PsiFile f : SliccProtocolIndex.globalFiles(project))
            addDeclNames(f, names);

        // Protocol scopes
        for (Set<PsiFile> group : SliccProtocolIndex.allProtocolGroups(project))
            for (PsiFile f : group)
                addDeclNames(f, names);

        return names.toArray(String[]::new);
    }

    @NotNull
    @Override
    public NavigationItem[] getItemsByName(String name, String pattern,
                                           Project project,
                                           boolean includeNonProjectItems) {
        List<NavigationItem> items = new ArrayList<>();

        for (PsiFile f : SliccProtocolIndex.globalFiles(project))
            collectNamed(f, name, items);

        for (Set<PsiFile> group : SliccProtocolIndex.allProtocolGroups(project))
            for (PsiFile f : group)
                collectNamed(f, name, items);

        return items.toArray(NavigationItem[]::new);
    }

    private void addDeclNames(PsiFile file, List<String> names) {
        addNames(PsiTreeUtil.collectElementsOfType(file, SliccMachineDecl.class), names);
        addNames(PsiTreeUtil.collectElementsOfType(file, SliccStructureDecl.class), names);
        addNames(PsiTreeUtil.collectElementsOfType(file, SliccEnumerationDecl.class), names);
        addNames(PsiTreeUtil.collectElementsOfType(file, SliccStateDecl.class), names);
        addNames(PsiTreeUtil.collectElementsOfType(file, SliccExternalTypeDecl.class), names);
        addNames(PsiTreeUtil.collectElementsOfType(file, SliccActionDecl.class), names);
        addNames(PsiTreeUtil.collectElementsOfType(file, SliccFunctionDecl.class), names);
    }

    private void addNames(java.util.Collection<? extends SliccNamedElement> elements,
                          List<String> names) {
        for (SliccNamedElement e : elements) {
            String n = e.getName();
            if (n != null && !names.contains(n)) names.add(n);
        }
    }

    private void collectNamed(PsiFile file, String name, List<NavigationItem> items) {
        for (SliccNamedElement e :
                PsiTreeUtil.collectElementsOfType(file, SliccNamedElement.class)) {
            if (name.equals(e.getName())) items.add(e);
        }
    }
}