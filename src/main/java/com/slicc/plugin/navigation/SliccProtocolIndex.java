package com.slicc.plugin.navigation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.slicc.plugin.lang.SliccFileType;
import com.slicc.plugin.lexer.SliccLexer;
import com.slicc.plugin.lexer.SliccTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SliccProtocolIndex {

    private static final Logger LOG = Logger.getInstance(SliccProtocolIndex.class);
    private static final String GLOBAL_ROOT = "RubySlicc_interfaces.slicc";
    private static final Key<CachedValue<ProjectGraph>> PROJECT_CACHE_KEY = Key.create("SLICC_PROJECT_GRAPH");
    private static final Key<CachedValue<FileNode>> FILE_CACHE_KEY = Key.create("SLICC_INCLUDE_NODE");
    private static final Key<CachedValue<Set<PsiFile>>> GROUP_CACHE_KEY = Key.create("SLICC_GROUP_CACHE");

    private record FileNode(List<PsiFile> includes, boolean machine) {}

    private record ProjectGraph(Set<PsiFile> files, Set<PsiFile> globals, List<Set<PsiFile>> groups,
                                Map<String, Set<PsiFile>> firstGroup,
                                Map<String, List<Set<PsiFile>>> machineChains,
                                Map<String, Set<PsiFile>> allMachineFiles) {}

    private SliccProtocolIndex() {}

    @NotNull
    public static Set<PsiFile> allProjectFiles(@NotNull Project project) {
        try {
            return graph(project).files();
        } catch (ProcessCanceledException exception) {
            throw exception;
        } catch (Exception exception) {
            LOG.debug("Unable to collect SLICC project files", exception);
            return Collections.emptySet();
        }
    }

    @NotNull
    public static Set<PsiFile> globalFiles(@NotNull Project project) {
        try {
            return graph(project).globals();
        } catch (ProcessCanceledException exception) {
            throw exception;
        } catch (Exception exception) {
            LOG.debug("Unable to collect SLICC global files", exception);
            return Collections.emptySet();
        }
    }

    @NotNull
    public static Set<PsiFile> groupContaining(@NotNull PsiFile file) {
        return CachedValuesManager.getCachedValue(file, GROUP_CACHE_KEY, () ->
                CachedValueProvider.Result.create(computeGroupContaining(file),
                        PsiModificationTracker.getInstance(file.getProject()),
                        VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
                        ProjectRootModificationTracker.getInstance(file.getProject()),
                        DumbService.getInstance(file.getProject()).getModificationTracker()));
    }

    @NotNull
    private static Set<PsiFile> computeGroupContaining(@NotNull PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile != null) {
            try {
                Set<PsiFile> group = graph(file.getProject()).firstGroup().get(virtualFile.getPath());
                if (group != null) return group;
            } catch (ProcessCanceledException exception) {
                throw exception;
            } catch (Exception exception) {
                LOG.debug("Unable to find SLICC protocol group for " + virtualFile.getPath(), exception);
            }
        }
        return fallback(file);
    }

    @NotNull
    public static Set<PsiFile> machineFilesContaining(@NotNull PsiFile file) {
        return machineFilesContaining(file, false);
    }

    @NotNull
    public static Set<PsiFile> allMachineFilesContaining(@NotNull PsiFile file) {
        return machineFilesContaining(file, true);
    }

    @NotNull
    private static Set<PsiFile> machineFilesContaining(@NotNull PsiFile file, boolean allMachines) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) return groupContaining(file);
        String path = virtualFile.getPath();
        try {
            ProjectGraph graph = graph(file.getProject());
            List<Set<PsiFile>> matches = graph.machineChains().get(path);
            if (matches != null && !matches.isEmpty()) {
                if (!allMachines) return matches.getFirst();
                return graph.allMachineFiles().computeIfAbsent(path, ignored -> {
                    Set<PsiFile> files = new LinkedHashSet<>();
                    for (Set<PsiFile> chain : matches) {
                        ProgressManager.checkCanceled();
                        files.addAll(chain);
                    }
                    return Collections.unmodifiableSet(files);
                });
            }
        } catch (ProcessCanceledException exception) {
            throw exception;
        } catch (Exception exception) {
            LOG.debug("Unable to find SLICC machine scope for " + path, exception);
        }
        return groupContaining(file);
    }

    private static Set<PsiFile> fallback(@NotNull PsiFile file) {
        Set<PsiFile> result = new LinkedHashSet<>();
        collectFromFile(file, result, new HashSet<>());
        return Collections.unmodifiableSet(result);
    }

    @NotNull
    public static List<Set<PsiFile>> allProtocolGroups(@NotNull Project project) {
        return graph(project).groups();
    }

    private static ProjectGraph graph(Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(project, PROJECT_CACHE_KEY, () ->
                CachedValueProvider.Result.create(buildGraph(project),
                        PsiModificationTracker.getInstance(project),
                        VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
                        ProjectRootModificationTracker.getInstance(project),
                        DumbService.getInstance(project).getModificationTracker()), false);
    }

    private static ProjectGraph buildGraph(Project project) {
        PsiManager manager = PsiManager.getInstance(project);
        Set<PsiFile> files = new LinkedHashSet<>();
        for (VirtualFile virtualFile : FileTypeIndex.getFiles(
                SliccFileType.INSTANCE, GlobalSearchScope.allScope(project))) {
            ProgressManager.checkCanceled();
            PsiFile file = manager.findFile(virtualFile);
            if (file != null) files.add(file);
        }
        Set<PsiFile> globals = null;
        List<Set<PsiFile>> groups = new ArrayList<>();
        Map<String, Set<PsiFile>> firstGroup = new HashMap<>();
        Map<String, List<Set<PsiFile>>> machineChains = new HashMap<>();
        for (PsiFile file : files) {
            ProgressManager.checkCanceled();
            VirtualFile virtualFile = file.getVirtualFile();
            if (virtualFile == null) continue;
            if (GLOBAL_ROOT.equals(virtualFile.getName())) {
                if (globals == null) globals = fallback(file);
            } else if ("slicc".equalsIgnoreCase(virtualFile.getExtension())) {
                Set<PsiFile> group = fallback(file);
                groups.add(group);
                for (PsiFile member : group) {
                    VirtualFile memberFile = member.getVirtualFile();
                    if (memberFile != null) firstGroup.putIfAbsent(memberFile.getPath(), group);
                }
            }
            if ("sm".equalsIgnoreCase(virtualFile.getExtension()) && node(file).machine()) {
                Set<PsiFile> chain = fallback(file);
                for (PsiFile member : chain) {
                    VirtualFile memberFile = member.getVirtualFile();
                    if (memberFile != null) {
                        machineChains.computeIfAbsent(memberFile.getPath(), ignored -> new ArrayList<>()).add(chain);
                    }
                }
            }
        }
        Map<String, List<Set<PsiFile>>> immutableChains = new HashMap<>();
        machineChains.forEach((path, chains) -> immutableChains.put(path, List.copyOf(chains)));
        return new ProjectGraph(Collections.unmodifiableSet(files), globals == null ? Set.of() : globals,
                List.copyOf(groups), Map.copyOf(firstGroup), Map.copyOf(immutableChains), new ConcurrentHashMap<>());
    }

    private static FileNode node(PsiFile file) {
        return CachedValuesManager.getCachedValue(file, FILE_CACHE_KEY, () -> {
            List<PsiFile> includes = new ArrayList<>();
            boolean machine = false;
            boolean includePath = false;
            CharSequence contents = file.getViewProvider().getContents();
            SliccLexer lexer = new SliccLexer();
            lexer.start(contents);
            while (lexer.getTokenType() != null) {
                ProgressManager.checkCanceled();
                IElementType token = lexer.getTokenType();
                if (token == SliccTokenTypes.WHITE_SPACE || token == SliccTokenTypes.LINE_COMMENT
                        || token == SliccTokenTypes.BLOCK_COMMENT) {
                    lexer.advance();
                    continue;
                }
                if (token == SliccTokenTypes.KW_MACHINE) machine = true;
                if (includePath && token == SliccTokenTypes.STRING_LITERAL
                        && lexer.getTokenEnd() - lexer.getTokenStart() >= 2) {
                    String path = contents.subSequence(lexer.getTokenStart() + 1, lexer.getTokenEnd() - 1).toString();
                    PsiFile included = resolveRelative(file, path);
                    if (included != null) includes.add(included);
                    else if (LOG.isDebugEnabled()) LOG.debug("Cannot resolve SLICC include '" + path + "' from " + file.getName());
                }
                includePath = token == SliccTokenTypes.KW_INCLUDE || token == SliccTokenTypes.KW_PROTOCOL;
                lexer.advance();
            }
            return CachedValueProvider.Result.create(new FileNode(List.copyOf(includes), machine), file,
                    VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS);
        });
    }

    static void collectFromFile(@NotNull PsiFile file,
                                @NotNull Set<PsiFile> out,
                                @NotNull Set<String> visitedPaths) {
        ProgressManager.checkCanceled();
        VirtualFile virtualFile = file.getVirtualFile();
        String path = virtualFile != null ? virtualFile.getPath() : file.getName();
        if (!visitedPaths.add(path)) return;
        out.add(file);
        for (PsiFile included : node(file).includes()) collectFromFile(included, out, visitedPaths);
    }

    @Nullable
    static PsiFile resolveRelative(@NotNull PsiFile from, @NotNull String relativePath) {
        VirtualFile virtualFile = from.getVirtualFile();
        if (virtualFile == null) return null;
        VirtualFile directory = virtualFile.getParent();
        if (directory == null) return null;
        VirtualFile target = directory.findFileByRelativePath(relativePath);
        if (target == null) return null;
        return PsiManager.getInstance(from.getProject()).findFile(target);
    }
}
