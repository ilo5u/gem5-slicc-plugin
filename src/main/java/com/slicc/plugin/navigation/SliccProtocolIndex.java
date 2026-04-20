package com.slicc.plugin.navigation;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.lang.SliccFileType;
import com.slicc.plugin.psi.SliccIncludeDecl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Two-level scope model for SLICC:
 *
 *  Global scope  — RubySlicc_interfaces.slicc and all files it includes.
 *                  Defines Cycles, MachineType, SpSequencer, DataBlock, etc.
 *                  Available everywhere. Located by searching all .slicc files
 *                  for the one named "RubySlicc_interfaces.slicc".
 *                  Falls back to treating ALL .sm files not owned by a protocol
 *                  as global if the root file is not found.
 *
 *  Protocol scope — Each protocol's *.slicc root (e.g. Tuice.slicc) and
 *                   all files reachable from it via include.
 *                   Overrides / extends the global scope.
 */
public class SliccProtocolIndex {

    private static final String GLOBAL_ROOT = "RubySlicc_interfaces.slicc";

    private SliccProtocolIndex() {}

    // ── Public API ────────────────────────────────────────────────────────

    @NotNull
    public static Set<PsiFile> globalFiles(@NotNull Project project) {
        PsiManager manager = PsiManager.getInstance(project);

        // Search all SLICC files (both .sm and .slicc) for the global root
        for (VirtualFile vf : FileTypeIndex.getFiles(
                SliccFileType.INSTANCE, GlobalSearchScope.allScope(project))) {
            if (GLOBAL_ROOT.equals(vf.getName())) {
                PsiFile root = manager.findFile(vf);
                if (root != null) {
                    Set<PsiFile> result = new LinkedHashSet<>();
                    collectFromFile(root, result, new HashSet<>());
                    return result;
                }
            }
        }
        return Collections.emptySet();
    }

    @NotNull
    public static Set<PsiFile> groupContaining(@NotNull PsiFile file) {
        Project project = file.getProject();
        for (Set<PsiFile> group : allProtocolGroups(project)) {
            if (group.contains(file)) return group;
        }
        // Fallback: self + own direct includes
        Set<PsiFile> fallback = new LinkedHashSet<>();
        collectFromFile(file, fallback, new HashSet<>());
        return fallback;
    }

    @NotNull
    public static List<Set<PsiFile>> allProtocolGroups(@NotNull Project project) {
        PsiManager manager = PsiManager.getInstance(project);
        List<Set<PsiFile>> result = new ArrayList<>();

        for (VirtualFile vf : FileTypeIndex.getFiles(
                SliccFileType.INSTANCE, GlobalSearchScope.allScope(project))) {
            if (!"slicc".equalsIgnoreCase(vf.getExtension())) continue;
            if (GLOBAL_ROOT.equals(vf.getName())) continue;
            PsiFile root = manager.findFile(vf);
            if (root == null) continue;
            Set<PsiFile> group = new LinkedHashSet<>();
            collectFromFile(root, group, new HashSet<>());
            if (!group.isEmpty()) result.add(group);
        }
        return result;
    }

    // ── Internal ──────────────────────────────────────────────────────────

    static void collectFromFile(@NotNull PsiFile file,
                                @NotNull Set<PsiFile> out,
                                @NotNull Set<String> visitedPaths) {
        VirtualFile vf = file.getVirtualFile();
        String path = vf != null ? vf.getPath() : file.getName();
        if (!visitedPaths.add(path)) return;
        out.add(file);

        for (SliccIncludeDecl inc :
                PsiTreeUtil.collectElementsOfType(file, SliccIncludeDecl.class)) {
            String rel = inc.getIncludedPath();
            if (rel == null) continue;
            PsiFile included = resolveRelative(file, rel);
            if (included != null) collectFromFile(included, out, visitedPaths);
        }
    }

    @Nullable
    static PsiFile resolveRelative(@NotNull PsiFile from,
                                   @NotNull String relativePath) {
        VirtualFile vf = from.getVirtualFile();
        if (vf == null) return null;
        VirtualFile dir = vf.getParent();
        if (dir == null) return null;
        VirtualFile target = dir.findFileByRelativePath(relativePath);
        if (target == null) return null;
        return PsiManager.getInstance(from.getProject()).findFile(target);
    }
}