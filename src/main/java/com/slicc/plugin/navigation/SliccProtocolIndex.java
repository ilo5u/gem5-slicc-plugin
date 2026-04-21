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

public class SliccProtocolIndex {

    private static final String GLOBAL_ROOT = "RubySlicc_interfaces.slicc";

    private SliccProtocolIndex() {}

    @NotNull
    public static Set<PsiFile> globalFiles(@NotNull Project project) {
        PsiManager manager = PsiManager.getInstance(project);
        for (VirtualFile vf : FileTypeIndex.getFiles(
                SliccFileType.INSTANCE, GlobalSearchScope.allScope(project))) {
            if (GLOBAL_ROOT.equals(vf.getName())) {
                PsiFile root = manager.findFile(vf);
                if (root != null) {
                    Set<PsiFile> result = new LinkedHashSet<>();
                    collectFromFile(root, result, new HashSet<>());
                    System.err.println("[SLICC-IDX] globalFiles size=" + result.size()
                            + " files=" + result.stream().map(f ->
                                    f.getVirtualFile() != null ? f.getVirtualFile().getName() : f.getName())
                            .toList());
                    return result;
                }
            }
        }
        System.err.println("[SLICC-IDX] globalFiles: EMPTY (RubySlicc_interfaces.slicc not found)");
        return Collections.emptySet();
    }

    @NotNull
    public static Set<PsiFile> groupContaining(@NotNull PsiFile file) {
        VirtualFile fileVf = file.getVirtualFile();
        if (fileVf == null) {
            System.err.println("[SLICC-IDX] groupContaining: file has no VirtualFile: " + file.getName());
            return fallback(file);
        }
        String filePath = fileVf.getPath();
        Project project = file.getProject();

        for (Set<PsiFile> group : allProtocolGroups(project)) {
            for (PsiFile member : group) {
                VirtualFile memberVf = member.getVirtualFile();
                if (memberVf != null && filePath.equals(memberVf.getPath())) {
                    System.err.println("[SLICC-IDX] groupContaining('"
                            + fileVf.getName() + "'): found group size=" + group.size());
                    return group;
                }
            }
        }

        System.err.println("[SLICC-IDX] groupContaining('"
                + fileVf.getName() + "'): NOT IN ANY GROUP -> fallback");
        return fallback(file);
    }

    private static Set<PsiFile> fallback(@NotNull PsiFile file) {
        Set<PsiFile> result = new LinkedHashSet<>();
        collectFromFile(file, result, new HashSet<>());
        return result;
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
            else System.err.println("[SLICC-IDX] cannot resolve include '" + rel
                    + "' from " + (vf != null ? vf.getName() : file.getName()));
        }
    }

    @Nullable
    static PsiFile resolveRelative(@NotNull PsiFile from, @NotNull String relativePath) {
        VirtualFile vf = from.getVirtualFile();
        if (vf == null) return null;
        VirtualFile dir = vf.getParent();
        if (dir == null) return null;
        VirtualFile target = dir.findFileByRelativePath(relativePath);
        if (target == null) return null;
        return PsiManager.getInstance(from.getProject()).findFile(target);
    }
}