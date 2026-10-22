package com.slicc.plugin.navigation;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class SliccRubyBuildScope {

    record Location(VirtualFile file, SliccCppTypes.Definition definition) {}
    record Snapshot(Map<String, List<Location>> definitions, List<Object> dependencies) {}
    private static final Key<Map<VirtualFile, CachedValue<Snapshot>>> CACHE = Key.create("SLICC_RUBY_CPP_SCOPE");
    private static final Set<String> CPP_EXTENSIONS = Set.of("h", "hh", "hpp", "hxx", "cc", "cpp", "cxx", "c");

    private SliccRubyBuildScope() {}

    @Nullable
    static VirtualFile rootFor(PsiFile file) {
        VirtualFile source = file.getOriginalFile().getVirtualFile();
        if (source == null) return null;
        String basePath = file.getProject().getBasePath();
        VirtualFile boundary = basePath == null ? null : LocalFileSystem.getInstance().findFileByPath(basePath);
        boolean searchCandidates = boundary == null || VfsUtilCore.isAncestor(boundary, source, false);
        for (VirtualFile directory = source.getParent(); directory != null; directory = directory.getParent()) {
            ProgressManager.checkCanceled();
            if (isRubyRoot(directory)) return directory;
            if (searchCandidates && directory.getParent() != null) {
                for (String path : List.of("mem/ruby", "src/mem/ruby", "ruby")) {
                    VirtualFile candidate = directory.findFileByRelativePath(path);
                    if (candidate != null && isRubyRoot(candidate) && configuredDirectory(candidate, source.getParent())) {
                        return candidate;
                    }
                }
            }
            if (directory.equals(boundary) || directory.findChild("SConstruct") != null) searchCandidates = false;
        }
        return null;
    }

    private static boolean isRubyRoot(VirtualFile directory) {
        return directory.findChild("Kconfig") != null && directory.findChild("SConscript") != null
                && directory.findChild("protocol") != null;
    }

    private static boolean configuredDirectory(VirtualFile root, VirtualFile directory) {
        Set<VirtualFile> visited = new HashSet<>();
        Deque<VirtualFile> pending = new ArrayDeque<>();
        pending.add(root.findChild("Kconfig"));
        while (!pending.isEmpty()) {
            ProgressManager.checkCanceled();
            VirtualFile config = pending.removeFirst();
            if (!visited.add(config)) continue;
            if (VfsUtilCore.isAncestor(config.getParent(), directory, false)) return true;
            pending.addAll(kconfigIncludes(config, root));
        }
        return false;
    }

    static Snapshot snapshot(VirtualFile root, Project project) {
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
        return cache.computeIfAbsent(root, directory -> CachedValuesManager.getManager(project).createCachedValue(() -> {
            Snapshot snapshot = new Builder(root).build();
            return CachedValueProvider.Result.create(snapshot, snapshot.dependencies.toArray());
        }, false)).getValue();
    }

    static List<VirtualFile> kconfigIncludes(VirtualFile config, VirtualFile root) {
        List<VirtualFile> result = new ArrayList<>();
        List<SliccSourceTokens.Token> tokens = SliccSourceTokens.read(text(config), true);
        for (int index = 0; index + 1 < tokens.size(); index++) {
            if (!Set.of("rsource", "orsource", "source", "osource").contains(tokens.get(index).text())
                    || !tokens.get(index + 1).literal()) continue;
            String path = tokens.get(index + 1).text();
            VirtualFile base = tokens.get(index).text().endsWith("rsource") ? config.getParent() : sourceRoot(root);
            VirtualFile target = base.findFileByRelativePath(path);
            if (target != null && !target.isDirectory()) result.add(target);
        }
        return result;
    }

    private static VirtualFile sourceRoot(VirtualFile root) {
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

    private static String text(VirtualFile file) {
        ProgressManager.checkCanceled();
        Document document = FileDocumentManager.getInstance().getCachedDocument(file);
        if (document != null) return document.getText();
        try {
            return com.intellij.openapi.util.text.StringUtil.convertLineSeparators(VfsUtilCore.loadText(file));
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read Ruby dependency '" + file.getPresentableUrl() + "'", exception);
        }
    }

    private static final class Builder {
        private final VirtualFile root;
        private final Map<String, List<Location>> definitions = new LinkedHashMap<>();
        private final Map<String, VirtualFile> generatedHeaders = new HashMap<>();
        private final Set<VirtualFile> read = new LinkedHashSet<>();
        private final Set<VirtualFile> scripts = new LinkedHashSet<>();
        private final Set<VirtualFile> visitedDirectories = new HashSet<>();
        private final Set<VirtualFile> seeds = new LinkedHashSet<>();
        private final List<Object> dependencies = new ArrayList<>();

        private Builder(VirtualFile root) {
            this.root = root;
            dependencies.add(VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS);
        }

        private Snapshot build() {
            collectScripts(root);
            Deque<VirtualFile> configs = new ArrayDeque<>();
            configs.add(root.findChild("Kconfig"));
            Set<VirtualFile> visitedConfigs = new HashSet<>();
            while (!configs.isEmpty()) {
                VirtualFile config = configs.removeFirst();
                if (!visitedConfigs.add(config)) continue;
                readText(config);
                collectScripts(config.getParent());
                configs.addAll(kconfigIncludes(config, root));
            }
            for (VirtualFile script : new ArrayList<>(scripts)) readScript(script);
            VirtualFile interfaces = root.findFileByRelativePath("slicc_interface/RubySlicc_includes.hh");
            if (interfaces != null) seeds.add(interfaces);
            Deque<VirtualFile> pending = new ArrayDeque<>(seeds);
            Set<VirtualFile> visited = new HashSet<>();
            while (!pending.isEmpty()) {
                VirtualFile file = pending.removeFirst();
                if (!visited.add(file)) continue;
                List<SliccSourceTokens.Token> tokens = SliccSourceTokens.read(readText(file), false);
                for (SliccCppTypes.Definition definition : SliccCppTypes.parse(tokens)) {
                    definitions.computeIfAbsent(definition.qualifiedName(), unused -> new ArrayList<>()).add(new Location(file, definition));
                }
                for (int index = 0; index + 1 < tokens.size(); index++) {
                    if (!tokens.get(index).text().equals("#include")) continue;
                    VirtualFile included = resolve(file.getParent(), tokens.get(index + 1).text());
                    if (included != null && cpp(included)) pending.add(included);
                }
            }
            return new Snapshot(definitions, dependencies);
        }

        private void collectScripts(VirtualFile directory) {
            if (!directory.isDirectory() || !visitedDirectories.add(directory)) return;
            ProgressManager.checkCanceled();
            for (VirtualFile child : directory.getChildren()) {
                if (child.isDirectory()) {
                    if (!child.getName().startsWith(".") && !Set.of("build", "ext", "node_modules").contains(child.getName())) {
                        collectScripts(child);
                    }
                } else if (child.getName().equals("SConscript") || child.getName().equals("SConsopts")) scripts.add(child);
            }
        }

        private void readScript(VirtualFile script) {
            List<SliccSourceTokens.Token> tokens = SliccSourceTokens.read(readText(script), true);
            for (int index = 0; index < tokens.size(); index++) {
                SliccSourceTokens.Token token = tokens.get(index);
                if (token.text().equals("SLICC_INCLUDES")) {
                    int cursor = index + 1;
                    if (token.literal()) {
                        if (index == 0 || !tokens.get(index - 1).text().equals("[") || cursor >= tokens.size()
                                || !tokens.get(cursor).text().equals("]")) continue;
                        cursor++;
                    }
                    if (cursor + 1 < tokens.size() && tokens.get(cursor).text().equals("+")
                            && tokens.get(cursor + 1).text().equals("=")) cursor++;
                    if (cursor < tokens.size() && tokens.get(cursor).text().equals("=")) {
                        List<String> paths = new ArrayList<>();
                        if (includeLiterals(tokens, cursor + 1, paths) >= 0) {
                            for (String path : paths) addSeed(script.getParent(), path, false);
                        }
                    }
                }
                if (!token.literal() && Set.of("Source", "MakeInclude").contains(token.text()) && index + 2 < tokens.size()
                        && tokens.get(index + 1).text().equals("(") && tokens.get(index + 2).literal()) {
                    addSeed(script.getParent(), tokens.get(index + 2).text(), token.text().equals("MakeInclude"));
                }
            }
        }

        private static int includeLiterals(List<SliccSourceTokens.Token> tokens, int index, List<String> paths) {
            if (index >= tokens.size()) return -1;
            SliccSourceTokens.Token token = tokens.get(index);
            if (token.literal()) {
                paths.add(token.text());
                return index + 1;
            }
            String closing = token.text().equals("[") ? "]" : token.text().equals("(") ? ")" : null;
            if (closing == null) return -1;
            int cursor = index + 1;
            while (cursor < tokens.size()) {
                if (tokens.get(cursor).text().equals(closing)) return cursor + 1;
                cursor = includeLiterals(tokens, cursor, paths);
                if (cursor < 0 || cursor >= tokens.size()) return -1;
                if (tokens.get(cursor).text().equals(closing)) return cursor + 1;
                if (!tokens.get(cursor).text().equals(",")) return -1;
                cursor++;
            }
            return -1;
        }

        private void addSeed(VirtualFile directory, String path, boolean generated) {
            VirtualFile file = resolve(directory, path);
            if (file == null || !cpp(file)) return;
            seeds.add(file);
            if (generated) generatedHeaders.put(file.getName(), file);
        }

        private VirtualFile resolve(VirtualFile directory, String path) {
            VirtualFile result = directory.findFileByRelativePath(path);
            if (result != null) return result;
            String prefix = "mem/ruby/";
            if (path.startsWith(prefix)) {
                result = root.findFileByRelativePath(path.substring(prefix.length()));
                if (result != null) return result;
                if (path.startsWith(prefix + "protocol/")) return generatedHeaders.get(path.substring(path.lastIndexOf('/') + 1));
            }
            result = root.findFileByRelativePath(path);
            if (result != null) return result;
            for (VirtualFile parent = root.getParent(); parent != null; parent = parent.getParent()) {
                if (parent.getName().equals("src")) return parent.findFileByRelativePath(path);
            }
            return null;
        }

        private String readText(VirtualFile file) {
            if (read.add(file)) {
                dependencies.add(file);
                dependencies.add((ModificationTracker) () -> {
                    Document document = FileDocumentManager.getInstance().getCachedDocument(file);
                    return document == null ? file.getModificationStamp() : document.getModificationStamp();
                });
            }
            return text(file);
        }

        private boolean cpp(VirtualFile file) {
            return !file.isDirectory() && CPP_EXTENSIONS.contains(extension(file.getName()));
        }

        private static String extension(String path) {
            int dot = path.lastIndexOf('.');
            return dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
    }
}
