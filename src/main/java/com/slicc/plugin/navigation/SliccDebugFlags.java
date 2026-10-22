package com.slicc.plugin.navigation;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SliccDebugFlags {

    public record Flag(String name, VirtualFile file, int offset) {}

    public record Snapshot(List<Flag> flags, boolean sourceAvailable) {
        public boolean contains(String name) {
            for (Flag flag : flags) {
                if (flag.name().equals(name)) return true;
            }
            return false;
        }
    }

    private record Root(List<VirtualFile> directories, boolean indexed, boolean known) {}
    private record Index(Snapshot snapshot, List<Object> dependencies) {}

    private static final Snapshot UNAVAILABLE = new Snapshot(List.of(), false);
    private static final Key<CachedValue<Root>> ROOT_CACHE = Key.create("SLICC_DEBUG_FLAG_ROOT");
    private static final Key<Map<Root, CachedValue<Snapshot>>> CACHE = Key.create("SLICC_DEBUG_FLAGS");
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of("build", "node_modules", "__pycache__");
    private static final Set<String> SCRIPT_NAMES = Set.of("SConscript", "SConsopts");
    private static final Set<String> FLAG_CALLS = Set.of("DebugFlag", "CompoundFlag");
    private static final Set<String> ARGUMENT_ENDINGS = Set.of(",", ")");

    private SliccDebugFlags() {}

    public static List<Flag> visible(PsiElement context) {
        return snapshot(context).flags();
    }

    public static boolean sourceAvailable(PsiElement context) {
        return snapshot(context).sourceAvailable();
    }

    public static Snapshot snapshot(PsiElement context) {
        ProgressManager.checkCanceled();
        Project project = context.getProject();
        if (DumbService.isDumb(project)) return UNAVAILABLE;
        PsiFile file = context.getContainingFile();
        if (file == null) return UNAVAILABLE;
        PsiFile original = file.getOriginalFile();
        Root root = CachedValuesManager.getCachedValue(original, ROOT_CACHE, () ->
                CachedValueProvider.Result.create(findRoot(original),
                        VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
                        ProjectRootModificationTracker.getInstance(project)));
        if (root.directories().isEmpty()) return UNAVAILABLE;
        Map<Root, CachedValue<Snapshot>> cache = project.getUserData(CACHE);
        if (cache == null) {
            synchronized (project) {
                cache = project.getUserData(CACHE);
                if (cache == null) {
                    cache = new ConcurrentHashMap<>();
                    project.putUserData(CACHE, cache);
                }
            }
        }
        try {
            return cache.computeIfAbsent(root, scope ->
                    CachedValuesManager.getManager(project).createCachedValue(() -> {
                        Index index = new Builder(scope, project).build();
                        return CachedValueProvider.Result.create(index.snapshot(), index.dependencies().toArray());
                    }, false)).getValue();
        } catch (IndexNotReadyException exception) {
            return UNAVAILABLE;
        }
    }

    private static Root findRoot(PsiFile file) {
        Project project = file.getProject();
        ProjectRootManager manager = ProjectRootManager.getInstance(project);
        if (manager != null) {
            List<VirtualFile> roots = new ArrayList<>();
            for (VirtualFile root : manager.getContentRoots()) {
                if (root.isValid() && root.isDirectory()) roots.add(root);
            }
            if (!roots.isEmpty()) return new Root(List.copyOf(roots), true, true);
        }
        VirtualFile original = file.getVirtualFile();
        if (original == null) return new Root(List.of(), false, false);
        String basePath = project.getBasePath();
        if (basePath != null) {
            VirtualFile base = original.getFileSystem().findFileByPath(basePath.replace('\\', '/'));
            if (base != null && base.isValid() && base.isDirectory()) {
                return new Root(List.of(base), false, true);
            }
        }
        for (VirtualFile directory = original.getParent(); directory != null; directory = directory.getParent()) {
            if (directory.findChild("SConstruct") != null) return new Root(List.of(directory), false, true);
        }
        VirtualFile directory = original.getParent();
        return new Root(directory == null ? List.of() : List.of(directory), false, false);
    }

    static Snapshot parse(String source, VirtualFile file) {
        List<Flag> flags = new ArrayList<>();
        boolean available = true;
        List<SliccSourceTokens.Token> tokens = SliccSourceTokens.read(source, true);
        for (int index = 0; index + 1 < tokens.size(); index++) {
            ProgressManager.checkCanceled();
            SliccSourceTokens.Token token = tokens.get(index);
            if (token.literal() || !FLAG_CALLS.contains(token.text())
                    || !tokens.get(index + 1).text().equals("(")) continue;
            if (index + 3 >= tokens.size() || !tokens.get(index + 2).literal()
                    || !ARGUMENT_ENDINGS.contains(tokens.get(index + 3).text())) {
                available = false;
                continue;
            }
            SliccSourceTokens.Token name = tokens.get(index + 2);
            if (!name.text().matches("[A-Za-z_][A-Za-z0-9_]*")) {
                available = false;
                continue;
            }
            flags.add(new Flag(name.text(), file, name.offset() + 1));
        }
        return new Snapshot(List.copyOf(flags), available);
    }

    private static final class Builder {
        private final Root root;
        private final Project project;
        private final ProjectFileIndex fileIndex;
        private final Map<String, Flag> flags = new LinkedHashMap<>();
        private final List<Object> dependencies = new ArrayList<>();
        private final Set<VirtualFile> scripts = new LinkedHashSet<>();
        private final Set<VirtualFile> visitedDirectories = new HashSet<>();
        private final Set<VirtualFile> readFiles = new HashSet<>();
        private boolean available;

        private Builder(Root root, Project project) {
            this.root = root;
            this.project = project;
            ProjectRootManager manager = ProjectRootManager.getInstance(project);
            this.fileIndex = manager == null ? null : manager.getFileIndex();
            this.available = root.known();
            dependencies.add(VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS);
            dependencies.add(ProjectRootModificationTracker.getInstance(project));
            dependencies.add(DumbService.getInstance(project).getModificationTracker());
        }

        private Index build() {
            if (root.indexed()) {
                GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
                for (String name : SCRIPT_NAMES) {
                    ProgressManager.checkCanceled();
                    scripts.addAll(FilenameIndex.getVirtualFilesByName(name, scope));
                }
            } else {
                for (VirtualFile directory : root.directories()) collectScripts(directory);
            }
            if (scripts.isEmpty()) available = false;
            List<VirtualFile> sortedScripts = new ArrayList<>(scripts);
            sortedScripts.sort(Comparator.comparing(VirtualFile::getPath));
            for (VirtualFile script : sortedScripts) {
                Snapshot parsed = parse(readText(script), script);
                available &= parsed.sourceAvailable();
                for (Flag flag : parsed.flags()) flags.putIfAbsent(flag.name(), flag);
            }
            return new Index(new Snapshot(List.copyOf(flags.values()), available), List.copyOf(dependencies));
        }

        private void collectScripts(VirtualFile directory) {
            ProgressManager.checkCanceled();
            if (directory == null || !directory.isDirectory() || !visitedDirectories.add(directory)) return;
            if (fileIndex != null && (fileIndex.isExcluded(directory) || fileIndex.isUnderIgnored(directory))) return;
            List<VirtualFile> children = new ArrayList<>(List.of(directory.getChildren()));
            children.sort(Comparator.comparing(VirtualFile::getName));
            for (VirtualFile child : children) {
                if (child.isDirectory()) {
                    if (!child.getName().startsWith(".") && !SKIPPED_DIRECTORIES.contains(child.getName())) collectScripts(child);
                } else if (SCRIPT_NAMES.contains(child.getName())
                        && (fileIndex == null || !fileIndex.isExcluded(child) && !fileIndex.isUnderIgnored(child))) scripts.add(child);
            }
        }

        private String readText(VirtualFile file) {
            ProgressManager.checkCanceled();
            if (readFiles.add(file)) {
                dependencies.add(file);
                dependencies.add((ModificationTracker) () -> {
                    Document document = FileDocumentManager.getInstance().getCachedDocument(file);
                    return document == null ? file.getModificationStamp() : document.getModificationStamp();
                });
            }
            Document document = FileDocumentManager.getInstance().getCachedDocument(file);
            if (document != null) return document.getText();
            try {
                return com.intellij.openapi.util.text.StringUtil.convertLineSeparators(VfsUtilCore.loadText(file));
            } catch (IOException exception) {
                available = false;
                return "";
            }
        }
    }
}
