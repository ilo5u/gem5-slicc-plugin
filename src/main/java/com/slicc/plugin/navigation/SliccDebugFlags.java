package com.slicc.plugin.navigation;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
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

    private record Root(VirtualFile directory) {}
    private record Index(Snapshot snapshot, List<Object> dependencies) {}

    private static final Snapshot UNAVAILABLE = new Snapshot(List.of(), false);
    private static final Key<CachedValue<Root>> ROOT_CACHE = Key.create("SLICC_DEBUG_FLAG_ROOT");
    private static final Key<Map<VirtualFile, CachedValue<Snapshot>>> CACHE = Key.create("SLICC_DEBUG_FLAGS");
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of("build", "ext", "node_modules", "__pycache__");
    private static final Set<String> CONFIG_INCLUDES = Set.of("source", "osource", "rsource", "orsource");
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
                CachedValueProvider.Result.create(new Root(SliccRubyBuildScope.rootFor(original)),
                        VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
                        ProjectRootModificationTracker.getInstance(project),
                        PsiModificationTracker.getInstance(project)));
        if (root.directory() == null || !root.directory().isValid()) return UNAVAILABLE;
        Map<VirtualFile, CachedValue<Snapshot>> cache = project.getUserData(CACHE);
        if (cache == null) {
            synchronized (project) {
                cache = project.getUserData(CACHE);
                if (cache == null) {
                    cache = new ConcurrentHashMap<>();
                    project.putUserData(CACHE, cache);
                }
            }
        }
        return cache.computeIfAbsent(root.directory(), directory ->
                CachedValuesManager.getManager(project).createCachedValue(() -> {
                    Index index = new Builder(directory, project).build();
                    return CachedValueProvider.Result.create(index.snapshot(), index.dependencies().toArray());
                }, false)).getValue();
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
        private final VirtualFile root;
        private final Map<String, Flag> flags = new LinkedHashMap<>();
        private final List<Object> dependencies = new ArrayList<>();
        private final Set<VirtualFile> scripts = new LinkedHashSet<>();
        private final Set<VirtualFile> visitedDirectories = new HashSet<>();
        private final Set<VirtualFile> readFiles = new HashSet<>();
        private boolean available = true;

        private Builder(VirtualFile root, Project project) {
            this.root = root;
            dependencies.add(VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS);
            dependencies.add(ProjectRootModificationTracker.getInstance(project));
        }

        private Index build() {
            collectScripts(root);
            Deque<VirtualFile> configs = new ArrayDeque<>();
            Set<VirtualFile> visitedConfigs = new HashSet<>();
            VirtualFile config = root.findChild("Kconfig");
            if (config != null) configs.add(config);
            while (!configs.isEmpty()) {
                ProgressManager.checkCanceled();
                VirtualFile current = configs.removeFirst();
                if (!visitedConfigs.add(current)) continue;
                collectScripts(current.getParent());
                List<SliccSourceTokens.Token> tokens = SliccSourceTokens.read(readText(current), true);
                for (int index = 0; index + 1 < tokens.size(); index++) {
                    SliccSourceTokens.Token token = tokens.get(index);
                    if (token.literal() || !CONFIG_INCLUDES.contains(token.text()) || !tokens.get(index + 1).literal()) continue;
                    VirtualFile base = token.text().endsWith("rsource") ? current.getParent() : sourceRoot();
                    String path = tokens.get(index + 1).text();
                    VirtualFile included = base.findFileByRelativePath(path);
                    if (included != null && !included.isDirectory()) configs.add(included);
                    else if (!token.text().startsWith("o")) available = false;
                }
            }
            for (VirtualFile script : scripts) {
                Snapshot parsed = parse(readText(script), script);
                available &= parsed.sourceAvailable();
                for (Flag flag : parsed.flags()) flags.putIfAbsent(flag.name(), flag);
            }
            return new Index(new Snapshot(List.copyOf(flags.values()), available), List.copyOf(dependencies));
        }

        private VirtualFile sourceRoot() {
            VirtualFile parent = root.getParent();
            if (parent != null && parent.getName().equals("mem") && parent.getParent() != null
                    && parent.getParent().getName().equals("src") && parent.getParent().getParent() != null) {
                return parent.getParent().getParent();
            }
            for (VirtualFile directory = root; directory != null; directory = directory.getParent()) {
                if (directory.findChild("SConstruct") != null) return directory;
            }
            return root;
        }

        private void collectScripts(VirtualFile directory) {
            ProgressManager.checkCanceled();
            if (directory == null || !directory.isDirectory() || !visitedDirectories.add(directory)) return;
            List<VirtualFile> children = new ArrayList<>(List.of(directory.getChildren()));
            children.sort(Comparator.comparing(VirtualFile::getName));
            for (VirtualFile child : children) {
                if (child.isDirectory()) {
                    if (!child.getName().startsWith(".") && !SKIPPED_DIRECTORIES.contains(child.getName())) collectScripts(child);
                } else if (child.getName().equals("SConscript") || child.getName().equals("SConsopts")) scripts.add(child);
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
