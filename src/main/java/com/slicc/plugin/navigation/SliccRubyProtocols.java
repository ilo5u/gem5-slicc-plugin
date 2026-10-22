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
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class SliccRubyProtocols {
    record Snapshot(Map<VirtualFile, Set<String>> protocolsByFile, Set<VirtualFile> machineFiles,
                    List<Object> dependencies) {}
    private static final Key<Map<VirtualFile, CachedValue<Snapshot>>> CACHE = Key.create("SLICC_RUBY_PROTOCOL_NAMESPACES");
    private static final Key<Map<VirtualFile, CachedValue<List<SliccSourceTokens.Token>>>> FILE_TOKENS =
            Key.create("SLICC_RUBY_PROTOCOL_TOKENS");

    private SliccRubyProtocols() {}

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
            Snapshot snapshot = new Builder(root, project).build();
            return CachedValueProvider.Result.create(snapshot, snapshot.dependencies().toArray());
        }, false)).getValue();
    }

    private static final class Builder {
        private final VirtualFile root;
        private final Project project;
        private final Map<String, Set<String>> protocolNames = new LinkedHashMap<>();
        private final Set<VirtualFile> protocolFiles = new LinkedHashSet<>();
        private final Set<VirtualFile> visitedDirectories = new HashSet<>();
        private final Set<VirtualFile> read = new HashSet<>();
        private final Map<VirtualFile, List<SliccSourceTokens.Token>> tokens = new HashMap<>();
        private final Map<VirtualFile, List<VirtualFile>> includes = new HashMap<>();
        private final Map<VirtualFile, Set<String>> protocolsByFile = new HashMap<>();
        private final Set<VirtualFile> machineFiles = new HashSet<>();
        private final List<Object> dependencies = new ArrayList<>();

        private Builder(VirtualFile root, Project project) {
            this.root = root;
            this.project = project;
            dependencies.add(VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS);
            dependencies.add(ProjectRootModificationTracker.getInstance(project));
            dependencies.add(DumbService.getInstance(project).getModificationTracker());
        }

        private Snapshot build() {
            collectProtocolRoots(root);
            Deque<VirtualFile> pending = new ArrayDeque<>();
            Set<VirtualFile> visited = new HashSet<>();
            pending.add(root.findChild("Kconfig"));
            while (!pending.isEmpty()) {
                ProgressManager.checkCanceled();
                VirtualFile config = pending.removeFirst();
                if (!visited.add(config)) continue;
                readProtocols(config);
                collectProtocolRoots(config.getParent());
                pending.addAll(SliccRubyBuildScope.kconfigIncludes(config, root));
            }
            for (VirtualFile protocolFile : protocolFiles) collectProtocol(protocolFile);
            for (VirtualFile source : new ArrayList<>(tokens.keySet())) {
                collectMachineIncludes(source);
            }
            return new Snapshot(protocolsByFile, machineFiles, dependencies);
        }

        private void collectProtocolRoots(VirtualFile directory) {
            if (!directory.isDirectory() || !visitedDirectories.add(directory)) return;
            ProgressManager.checkCanceled();
            for (VirtualFile child : directory.getChildren()) {
                if (child.isDirectory()) {
                    if (!child.getName().startsWith(".") && !Set.of("build", "ext", "node_modules").contains(child.getName())) {
                        collectProtocolRoots(child);
                    }
                } else if ("slicc".equalsIgnoreCase(child.getExtension())) protocolFiles.add(child);
            }
        }

        private void readProtocols(VirtualFile config) {
            List<SliccSourceTokens.Token> configTokens = SliccSourceTokens.read(text(config), true);
            String current = null;
            for (int index = 0; index < configTokens.size(); index++) {
                ProgressManager.checkCanceled();
                SliccSourceTokens.Token token = configTokens.get(index);
                if (token.literal()) continue;
                if (Set.of("config", "menuconfig").contains(token.text())) {
                    current = index + 1 < configTokens.size() && !configTokens.get(index + 1).literal()
                            && configTokens.get(index + 1).text().startsWith("RUBY_PROTOCOL_")
                            ? configTokens.get(index + 1).text().substring("RUBY_PROTOCOL_".length()) : null;
                    if (current != null && current.isEmpty()) current = null;
                    if (current != null && !current.isEmpty()) {
                        protocolNames.computeIfAbsent(current, unused -> new LinkedHashSet<>()).add(current);
                    }
                } else if (current != null && token.text().equals("bool") && index + 1 < configTokens.size()
                        && configTokens.get(index + 1).literal()) {
                    String prompt = normalize(configTokens.get(index + 1).text());
                    protocolNames.computeIfAbsent(prompt, unused -> new LinkedHashSet<>()).add(current);
                }
            }
        }

        private void collectProtocol(VirtualFile protocolFile) {
            List<SliccSourceTokens.Token> sourceTokens = tokens(protocolFile);
            Set<String> names = new LinkedHashSet<>();
            boolean declared = false;
            for (int index = 0; index + 1 < sourceTokens.size(); index++) {
                if (!sourceTokens.get(index).literal() && sourceTokens.get(index).text().equals("protocol")
                        && sourceTokens.get(index + 1).literal()) {
                    declared = true;
                    Set<String> configured = protocolNames.get(normalize(sourceTokens.get(index + 1).text()));
                    names.addAll(configured == null || configured.isEmpty() ? Set.of("") : configured);
                }
            }
            if (!declared) return;
            Deque<VirtualFile> pending = new ArrayDeque<>();
            Set<VirtualFile> visited = new HashSet<>();
            pending.add(protocolFile);
            while (!pending.isEmpty()) {
                ProgressManager.checkCanceled();
                VirtualFile source = pending.removeFirst();
                if (!visited.add(source)) continue;
                protocolsByFile.computeIfAbsent(source, unused -> new LinkedHashSet<>()).addAll(names);
                pending.addAll(includes(source));
            }
        }

        private void collectMachineIncludes(VirtualFile source) {
            Deque<VirtualFile> pending = new ArrayDeque<>();
            List<SliccSourceTokens.Token> sourceTokens = tokens(source);
            boolean pendingMachine = false;
            int depth = 0;
            int machineDepth = -1;
            for (int index = 0; index < sourceTokens.size(); index++) {
                SliccSourceTokens.Token token = sourceTokens.get(index);
                if (token.literal()) continue;
                if (token.text().equals("machine")) pendingMachine = true;
                else if (token.text().equals("{")) {
                    depth++;
                    if (pendingMachine) {
                        machineDepth = depth;
                        pendingMachine = false;
                    }
                } else if (token.text().equals("}")) {
                    if (depth == machineDepth) machineDepth = -1;
                    depth--;
                } else if (token.text().equals("include") && machineDepth > 0 && index + 1 < sourceTokens.size()
                        && sourceTokens.get(index + 1).literal()) {
                    VirtualFile included = source.getParent().findFileByRelativePath(sourceTokens.get(index + 1).text());
                    if (included != null && includes(source).contains(included)) pending.add(included);
                }
            }
            while (!pending.isEmpty()) {
                ProgressManager.checkCanceled();
                VirtualFile included = pending.removeFirst();
                if (!machineFiles.add(included)) continue;
                pending.addAll(includes(included));
            }
        }

        private List<VirtualFile> includes(VirtualFile source) {
            return includes.computeIfAbsent(source, file -> {
                List<VirtualFile> result = new ArrayList<>();
                List<SliccSourceTokens.Token> sourceTokens = tokens(file);
                for (int index = 0; index + 1 < sourceTokens.size(); index++) {
                    if (sourceTokens.get(index).literal() || !sourceTokens.get(index).text().equals("include")
                            || !sourceTokens.get(index + 1).literal()) continue;
                    VirtualFile included = file.getParent().findFileByRelativePath(sourceTokens.get(index + 1).text());
                    if (included != null && !included.isDirectory() && included.getExtension() != null
                            && Set.of("sm", "slicc").contains(included.getExtension().toLowerCase(Locale.ROOT))) result.add(included);
                }
                return result;
            });
        }

        private List<SliccSourceTokens.Token> tokens(VirtualFile file) {
            return tokens.computeIfAbsent(file, source -> {
                track(source);
                Map<VirtualFile, CachedValue<List<SliccSourceTokens.Token>>> cache = project.getUserData(FILE_TOKENS);
                if (cache == null) {
                    synchronized (project) {
                        cache = project.getUserData(FILE_TOKENS);
                        if (cache == null) {
                            cache = new ConcurrentHashMap<>();
                            project.putUserData(FILE_TOKENS, cache);
                        }
                    }
                }
                CachedValuesManager manager = CachedValuesManager.getManager(project);
                return cache.computeIfAbsent(source, dependency -> manager.createCachedValue(() ->
                        CachedValueProvider.Result.create(SliccSourceTokens.read(sourceText(source), false), source,
                                documentTracker(source)), false)).getValue();
            });
        }

        private String text(VirtualFile file) {
            track(file);
            return sourceText(file);
        }

        private void track(VirtualFile file) {
            if (read.add(file)) {
                dependencies.add(file);
                dependencies.add(documentTracker(file));
            }
        }

        private static ModificationTracker documentTracker(VirtualFile file) {
            return () -> {
                Document document = FileDocumentManager.getInstance().getCachedDocument(file);
                return document == null ? file.getModificationStamp() : document.getModificationStamp();
            };
        }

        private static String sourceText(VirtualFile file) {
            Document document = FileDocumentManager.getInstance().getCachedDocument(file);
            if (document != null) return document.getText();
            try {
                return VfsUtilCore.loadText(file);
            } catch (IOException exception) {
                throw new UncheckedIOException("Cannot read Ruby protocol dependency '" + file.getPresentableUrl() + "'", exception);
            }
        }

        private static String normalize(String name) {
            return name.trim().replaceAll("\\s+", "_");
        }
    }
}
