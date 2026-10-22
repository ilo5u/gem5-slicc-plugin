package com.slicc.plugin.navigation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.psi.SliccActionDecl;
import com.slicc.plugin.psi.SliccIncludeDecl;
import com.slicc.plugin.psi.SliccMachineDecl;
import com.slicc.plugin.psi.SliccPsiUtil;
import com.slicc.plugin.psi.SliccTransitionDecl;
import com.slicc.plugin.psi.SliccTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SliccTransitionDiagnostics {
    private static final Key<CachedValue<Map<SliccTransitionDecl, List<Diagnostic>>>> CACHE =
            Key.create("SLICC_TRANSITION_DIAGNOSTICS");

    private SliccTransitionDiagnostics() {}

    public record Diagnostic(@NotNull TextRange range, @NotNull String message) {}

    @NotNull
    public static List<Diagnostic> check(@NotNull SliccTransitionDecl transition,
                                         @Nullable AnnotationSession session) {
        ProgressManager.checkCanceled();
        if (DumbService.isDumb(transition.getProject())) return List.of();
        PsiFile file = transition.getContainingFile();
        if (file == null) return List.of();
        Map<SliccTransitionDecl, List<Diagnostic>> results = CachedValuesManager.getCachedValue(file, CACHE, () ->
                CachedValueProvider.Result.create(analyze(file), file, PsiModificationTracker.MODIFICATION_COUNT,
                        VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
                        ProjectRootModificationTracker.getInstance(file.getProject()),
                        DumbService.getInstance(file.getProject()).getModificationTracker()));
        return results.getOrDefault(transition, List.of());
    }

    private static Map<SliccTransitionDecl, List<Diagnostic>> analyze(PsiFile file) {
        Set<PsiFile> files = new LinkedHashSet<>();
        files.add(file);
        files.addAll(SliccProtocolIndex.allMachineFilesContaining(file));
        files.addAll(SliccProtocolIndex.groupContaining(file));
        Map<SliccMachineDecl, Expansion> machines = new LinkedHashMap<>();
        for (PsiFile candidate : files) {
            for (SliccMachineDecl machine : SliccFileSymbols.ofType(candidate, SliccMachineDecl.class)) {
                ProgressManager.checkCanceled();
                Expansion expansion = new Expansion(machine);
                expansion.expand(candidate);
                machines.put(machine, expansion);
            }
        }
        Map<Expansion, Map<SliccTransitionDecl, List<Diagnostic>>> indexes = new HashMap<>();
        Map<SliccTransitionDecl, List<Diagnostic>> results = new HashMap<>();
        Expansion standalone = null;
        for (SliccTransitionDecl transition : SliccFileSymbols.ofType(file, SliccTransitionDecl.class)) {
            ProgressManager.checkCanceled();
            SliccMachineDecl owner = PsiTreeUtil.getParentOfType(transition, SliccMachineDecl.class);
            Expansion selected = owner == null ? null : machines.get(owner);
            if (owner == null) {
                boolean ambiguous = false;
                for (Expansion expansion : machines.values()) {
                    if (!expansion.transitions.contains(transition)) continue;
                    if (selected != null) {
                        ambiguous = true;
                        break;
                    }
                    selected = expansion;
                }
                if (ambiguous) continue;
                if (selected == null) {
                    if (standalone == null) {
                        standalone = new Expansion(null);
                        standalone.expand(file);
                    }
                    selected = standalone;
                }
            }
            if (selected == null || !selected.complete) continue;
            Map<SliccTransitionDecl, List<Diagnostic>> index = indexes.computeIfAbsent(selected,
                    SliccTransitionDiagnostics::index);
            results.put(transition, index.getOrDefault(transition, List.of()));
        }
        return Map.copyOf(results);
    }

    private static Map<SliccTransitionDecl, List<Diagnostic>> index(Expansion expansion) {
        Map<SliccTransitionDecl, List<Diagnostic>> results = new HashMap<>();
        Set<Pair> pairs = new HashSet<>();
        for (SliccTransitionDecl transition : expansion.transitions) {
            ProgressManager.checkCanceled();
            Header header = header(transition);
            if (header == null) continue;
            List<Diagnostic> diagnostics = new ArrayList<>();
            for (ASTNode action : header.actions()) {
                if (!expansion.actions.contains(action.getText())) {
                    diagnostics.add(new Diagnostic(action.getTextRange(),
                            "Unknown transition action '" + action.getText() + "'"));
                }
            }
            Set<Pair> current = new LinkedHashSet<>();
            for (String state : header.states()) {
                for (String event : header.events()) {
                    ProgressManager.checkCanceled();
                    Pair pair = new Pair(state, event);
                    if (pairs.contains(pair)) current.add(pair);
                }
            }
            if (!current.isEmpty()) {
                Pair first = current.iterator().next();
                String extra = current.size() > 1 ? " (and " + (current.size() - 1) + " more)" : "";
                diagnostics.add(new Diagnostic(header.range(), "Duplicate transition for state '"
                        + first.state() + "' and event '" + first.event() + "'" + extra));
            }
            for (String state : header.states()) {
                for (String event : header.events()) pairs.add(new Pair(state, event));
            }
            results.put(transition, List.copyOf(diagnostics));
        }
        return Map.copyOf(results);
    }

    private record Pair(String state, String event) {}

    private record Header(List<String> states, List<String> events, List<ASTNode> actions, TextRange range) {}

    @Nullable
    private static Header header(SliccTransitionDecl transition) {
        if (PsiTreeUtil.findChildOfType(transition, PsiErrorElement.class) != null) return null;
        ASTNode node = transition.getNode();
        ASTNode states = node.findChildByType(SliccTypes.TRANSITION_STATES);
        ASTNode events = node.findChildByType(SliccTypes.TRANSITION_EVENTS);
        ASTNode closing = node.findChildByType(SliccTokenTypes.OP_RPAREN);
        if (states == null || events == null || closing == null) return null;
        List<String> stateNames = names(states);
        List<String> eventNames = names(events);
        if (stateNames.isEmpty() || eventNames.isEmpty()) return null;
        ASTNode actions = node.findChildByType(SliccTypes.TRANSITION_ACTIONS);
        if (actions == null) {
            ASTNode resources = node.findChildByType(SliccTypes.RESOURCE_LIST);
            if (resources == null || leaves(resources).size() != 2) return null;
            actions = resources;
        }
        List<ASTNode> tokens = leaves(actions);
        if (tokens.size() < 2 || tokens.getFirst().getElementType() != SliccTokenTypes.OP_LBRACE
                || tokens.getLast().getElementType() != SliccTokenTypes.OP_RBRACE) return null;
        if (tokens.size() > 2 && names(actions).isEmpty()) return null;
        List<ASTNode> actionNames = new ArrayList<>();
        for (int index = 1; index + 1 < tokens.size(); index++) {
            ASTNode token = tokens.get(index);
            if (token.getElementType() == SliccTokenTypes.IDENTIFIER) actionNames.add(token);
        }
        return new Header(stateNames, eventNames, List.copyOf(actionNames),
                new TextRange(transition.getTextOffset(), closing.getTextRange().getEndOffset()));
    }

    private static List<String> names(ASTNode list) {
        List<ASTNode> tokens = leaves(list);
        if (tokens.size() == 1 && tokens.getFirst().getElementType() == SliccTokenTypes.IDENTIFIER) {
            return List.of(tokens.getFirst().getText());
        }
        if (tokens.size() < 3 || tokens.getFirst().getElementType() != SliccTokenTypes.OP_LBRACE
                || tokens.getLast().getElementType() != SliccTokenTypes.OP_RBRACE) return List.of();
        Set<String> names = new LinkedHashSet<>();
        boolean expectName = true;
        for (int index = 1; index + 1 < tokens.size(); index++) {
            ASTNode token = tokens.get(index);
            if (token.getElementType() == SliccTokenTypes.IDENTIFIER) {
                names.add(token.getText());
                expectName = false;
            } else if (!expectName && (token.getElementType() == SliccTokenTypes.OP_COMMA
                    || token.getElementType() == SliccTokenTypes.OP_SEMICOLON)) {
                expectName = true;
            } else {
                return List.of();
            }
        }
        return List.copyOf(names);
    }

    private static List<ASTNode> leaves(ASTNode node) {
        List<ASTNode> tokens = new ArrayList<>();
        collectLeaves(node, tokens);
        return tokens;
    }

    private static void collectLeaves(ASTNode node, List<ASTNode> tokens) {
        ProgressManager.checkCanceled();
        if (SliccPsiUtil.isTrivia(node)) return;
        if (node.getFirstChildNode() == null) {
            tokens.add(node);
            return;
        }
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            collectLeaves(child, tokens);
        }
    }

    private static final class Expansion {
        private final SliccMachineDecl owner;
        private final Set<PsiFile> visited = new HashSet<>();
        private final Set<PsiFile> active = new HashSet<>();
        private final Set<String> actions = new HashSet<>();
        private final List<SliccTransitionDecl> transitions = new ArrayList<>();
        private boolean complete = true;

        private Expansion(SliccMachineDecl owner) {
            this.owner = owner;
            if (owner != null && owner.getNode().findChildByType(SliccTokenTypes.OP_RBRACE) == null) complete = false;
        }

        private void expand(PsiFile file) {
            ProgressManager.checkCanceled();
            if (active.contains(file)) {
                complete = false;
                return;
            }
            if (!visited.add(file)) return;
            active.add(file);
            collect(file);
            active.remove(file);
        }

        private void collect(PsiElement container) {
            for (PsiElement child : container.getChildren()) {
                ProgressManager.checkCanceled();
                if (child instanceof SliccMachineDecl machine) {
                    if (machine == owner) collect(machine);
                } else if (child instanceof SliccIncludeDecl include) {
                    String path = include.getIncludedPath();
                    PsiFile target = path == null ? null
                            : SliccProtocolIndex.resolveRelative(include.getContainingFile(), path);
                    if (target == null) complete = false;
                    else expand(target);
                } else if (child instanceof SliccActionDecl action) {
                    if (action.getName() != null) actions.add(action.getName());
                } else if (child instanceof SliccTransitionDecl transition) {
                    transitions.add(transition);
                }
            }
        }
    }
}
