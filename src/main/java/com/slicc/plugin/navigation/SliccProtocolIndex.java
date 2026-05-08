package com.slicc.plugin.navigation;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.lang.SliccFileType;
import com.slicc.plugin.psi.SliccIncludeDecl;
import com.slicc.plugin.psi.SliccMachineDecl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SliccProtocolIndex {

    private static final String GLOBAL_ROOT = "RubySlicc_interfaces.slicc";

    private static final Key<CachedValue<Set<PsiFile>>> GROUP_CACHE_KEY =
            Key.create("SLICC_GROUP_CACHE");
    private static final Key<CachedValue<Set<PsiFile>>> GLOBAL_CACHE_KEY =
            Key.create("SLICC_GLOBAL_CACHE");

    private SliccProtocolIndex() {}

    /** All .sm/.slicc files in the project — used as a last-resort fallback. */
    @NotNull
    public static Set<PsiFile> allProjectFiles(@NotNull Project project) {
        PsiManager manager = PsiManager.getInstance(project);
        Set<PsiFile> result = new LinkedHashSet<>();
        try {
            for (VirtualFile vf : FileTypeIndex.getFiles(
                    SliccFileType.INSTANCE, GlobalSearchScope.allScope(project))) {
                PsiFile f = manager.findFile(vf);
                if (f != null) result.add(f);
            }
        } catch (Exception e) {
            System.err.println("[SLICC-IDX] allProjectFiles error: " + e);
        }
        return result;
    }

    @NotNull
    public static Set<PsiFile> globalFiles(@NotNull Project project) {
        // Use any stable PsiFile as the cache holder — pick the global root if found
        PsiManager manager = PsiManager.getInstance(project);
        try {
            for (VirtualFile vf : FileTypeIndex.getFiles(
                    SliccFileType.INSTANCE, GlobalSearchScope.allScope(project))) {
                if (GLOBAL_ROOT.equals(vf.getName())) {
                    PsiFile root = manager.findFile(vf);
                    if (root != null) {
                        return CachedValuesManager.getCachedValue(root, GLOBAL_CACHE_KEY, () -> {
                            Set<PsiFile> result = new LinkedHashSet<>();
                            collectFromFile(root, result, new HashSet<>());
                            return CachedValueProvider.Result.create(result,
                                    PsiModificationTracker.getInstance(project));
                        });
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[SLICC-IDX] globalFiles error: " + e);
        }
        return Collections.emptySet();
    }

    /**
     * Returns the set of files that belong to the same protocol as {@code file}.
     * Result is cached per file and invalidated on any PSI modification.
     */
    @NotNull
    public static Set<PsiFile> groupContaining(@NotNull PsiFile file) {
        return CachedValuesManager.getCachedValue(file, GROUP_CACHE_KEY, () -> {
            Set<PsiFile> group = computeGroupContaining(file);
            return CachedValueProvider.Result.create(group,
                    PsiModificationTracker.getInstance(file.getProject()));
        });
    }

    @NotNull
    private static Set<PsiFile> computeGroupContaining(@NotNull PsiFile file) {
        VirtualFile fileVf = file.getVirtualFile();
        if (fileVf == null) return fallback(file);

        String filePath = fileVf.getPath();
        Project project = file.getProject();

        try {
            for (Set<PsiFile> group : allProtocolGroups(project)) {
                for (PsiFile member : group) {
                    VirtualFile memberVf = member.getVirtualFile();
                    if (memberVf != null && filePath.equals(memberVf.getPath())) {
                        return group;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[SLICC-IDX] groupContaining error for "
                    + fileVf.getName() + ": " + e);
        }
        return fallback(file);
    }

    /**
     * Returns the set of files in the same machine's include chain as {@code file}.
     * e.g. if llc-func.sm is included by llc.sm (SpLLCache machine), this returns
     * {llc.sm, llc-func.sm, ...} — excluding pri-cache.sm and other machines.
     * Falls back to groupContaining if no machine root is found.
     */
    @NotNull
    public static Set<PsiFile> machineFilesContaining(@NotNull PsiFile file) {
        VirtualFile fileVf = file.getVirtualFile();
        if (fileVf == null) return groupContaining(file);
        String filePath = fileVf.getPath();
        Project project = file.getProject();
        PsiManager manager = PsiManager.getInstance(project);

        try {
            for (VirtualFile vf : FileTypeIndex.getFiles(
                    SliccFileType.INSTANCE, GlobalSearchScope.allScope(project))) {
                if (!"sm".equalsIgnoreCase(vf.getExtension())) continue;
                PsiFile candidate = manager.findFile(vf);
                if (candidate == null) continue;
                if (PsiTreeUtil.collectElementsOfType(candidate, SliccMachineDecl.class).isEmpty()) continue;

                Set<PsiFile> chain = new LinkedHashSet<>();
                collectFromFile(candidate, chain, new HashSet<>());

                boolean containsFile = chain.stream().anyMatch(f -> {
                    VirtualFile fvf = f.getVirtualFile();
                    return fvf != null && fvf.getPath().equals(filePath);
                });
                if (containsFile) {
                    System.err.println("[SLICC-IDX] machine scope for " + fileVf.getName()
                            + " rooted at " + vf.getName() + " (" + chain.size() + " files)");
                    return chain;
                }
            }
        } catch (Exception e) {
            System.err.println("[SLICC-IDX] machineFilesContaining error: " + e);
        }
        return groupContaining(file);
    }

    private static Set<PsiFile> fallback(@NotNull PsiFile file) {        Set<PsiFile> result = new LinkedHashSet<>();
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