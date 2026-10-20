package com.slicc.plugin.navigation;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class SliccFunctionNavigation {

    private static final Key<CachedValue<ScopeData>> SCOPE_CACHE = Key.create("SLICC_FUNCTION_SCOPE");

    private SliccFunctionNavigation() {}

    @NotNull
    public static List<SliccFunctionDecl> definitions(@NotNull SliccFunctionDecl declaration) {
        if (declaration.isDefinition()) return List.of(declaration);
        Scope scope = new Scope(declaration);
        List<SliccFunctionDecl> result = new ArrayList<>();
        for (PsiFile file : scope.files) {
            for (SliccFunctionDecl candidate : PsiTreeUtil.collectElementsOfType(file, SliccFunctionDecl.class)) {
                ProgressManager.checkCanceled();
                if (candidate.isDefinition() && sameSignature(declaration, candidate)
                        && scope.sameOwner(declaration, candidate)) result.add(candidate);
            }
        }
        return result.isEmpty() ? List.of(declaration) : result;
    }

    public static boolean isSameFunction(SliccFunctionDecl first, SliccFunctionDecl second) {
        if (equivalent(first, second)) return true;
        if (!sameSignature(first, second)) return false;
        Scope scope = new Scope(first);
        return scope.files.contains(second.getContainingFile()) && scope.sameOwner(first, second);
    }

    @NotNull
    public static List<SliccFunctionDecl> resolveCall(@NotNull PsiElement call) {
        List<String> arguments = SliccGotoDeclarationHandler.extractCallArgTokens(call);
        if (arguments == null) return List.of();
        Scope scope = new Scope(call);
        ASTNode previous = SliccPsiUtil.previousSignificantSibling(call.getNode());
        boolean member = previous != null && previous.getElementType() == SliccTokenTypes.OP_DOT;
        boolean scoped = previous != null && previous.getElementType() == SliccTokenTypes.OP_SCOPE;
        List<SliccFunctionDecl> candidates;
        if (member || scoped) {
            String type = member ? SliccGotoDeclarationHandler.resolveDotOwnerType(call) : scopedOwner(previous);
            candidates = type == null ? List.of() : scope.members(type, call.getText(), new HashSet<>());
        } else {
            PsiElement owner = typeOwner(call);
            candidates = owner == null ? List.of() : directMembers(owner, call.getText());
            if (candidates.isEmpty() && owner instanceof SliccStructureDecl structure) {
                String parent = SliccGotoDeclarationHandler.getStructureInterface(structure);
                if (parent != null) candidates = scope.members(parent, call.getText(), new HashSet<>());
            }
            if (candidates.isEmpty()) candidates = scope.freeFunctions(call.getText());
        }
        int bestScore = -1;
        List<SliccFunctionDecl> best = new ArrayList<>();
        for (SliccFunctionDecl candidate : candidates) {
            ProgressManager.checkCanceled();
            int score = SliccGotoDeclarationHandler.scoreOverload(candidate, arguments, call);
            if (score < 0 || score < bestScore) continue;
            if (score > bestScore) {
                best.clear();
                bestScore = score;
            }
            best.add(candidate);
        }
        Set<SliccFunctionDecl> result = new LinkedHashSet<>();
        for (SliccFunctionDecl candidate : best) {
            for (SliccFunctionDecl definition : definitions(candidate)) {
                if (scope.rank(definition) >= 0) result.add(definition);
            }
        }
        return new ArrayList<>(result);
    }

    @NotNull
    public static SliccCallSite[] findCallSites(@NotNull SliccFunctionDecl function) {
        Set<PsiFile> files = new LinkedHashSet<>(new Scope(function).files);
        files.addAll(SliccProtocolIndex.allProjectFiles(function.getProject()));
        Set<PsiElement> statements = new LinkedHashSet<>();
        List<SliccCallSite> targets = new ArrayList<>();
        for (PsiFile file : files) {
            for (PsiElement reference : PsiTreeUtil.collectElements(file,
                    element -> function.getName() != null && function.getName().equals(element.getText())
                            && element.getFirstChild() == null)) {
                ProgressManager.checkCanceled();
                SliccNamedElement caller = PsiTreeUtil.getParentOfType(reference,
                        SliccFunctionDecl.class, SliccActionDecl.class);
                if (caller == null || reference.equals(caller.getNameIdentifier())) continue;
                if (SliccGotoDeclarationHandler.extractCallArgTokens(reference) == null) continue;
                PsiElement resolved = SliccGotoDeclarationHandler.resolveReference(reference);
                if (resolved instanceof SliccFunctionDecl target && isSameFunction(function, target)
                        && statements.add(SliccCallSite.statementOf(reference))) {
                    targets.add(new SliccCallSite(reference));
                }
            }
        }
        return targets.toArray(SliccCallSite[]::new);
    }

    private static boolean sameSignature(SliccFunctionDecl first, SliccFunctionDecl second) {
        return Objects.equals(first.getName(), second.getName())
                && first.getParameterTypes().equals(second.getParameterTypes());
    }

    private static boolean equivalent(PsiElement first, PsiElement second) {
        return first != null && second != null
                && PsiManager.getInstance(first.getProject()).areElementsEquivalent(first, second);
    }

    private static PsiElement typeOwner(PsiElement element) {
        return PsiTreeUtil.getParentOfType(element, SliccStructureDecl.class, SliccExternalTypeDecl.class);
    }

    private static String scopedOwner(ASTNode separator) {
        ASTNode owner = SliccPsiUtil.previousSignificantSibling(separator);
        if (owner == null) return null;
        StringBuilder name = new StringBuilder(owner.getText());
        ASTNode previous = SliccPsiUtil.previousSignificantSibling(owner);
        while (previous != null && previous.getElementType() == SliccTokenTypes.OP_SCOPE) {
            owner = SliccPsiUtil.previousSignificantSibling(previous);
            if (owner == null) break;
            name.insert(0, owner.getText() + "::");
            previous = SliccPsiUtil.previousSignificantSibling(owner);
        }
        return name.toString();
    }

    private static List<SliccFunctionDecl> directMembers(PsiElement owner, String name) {
        List<SliccFunctionDecl> result = new ArrayList<>();
        for (PsiElement child : owner.getChildren()) {
            if (child instanceof SliccFunctionDecl function && name.equals(function.getName())) result.add(function);
        }
        return result;
    }

    private static final class ScopeData {
        private final Set<PsiFile> files = new LinkedHashSet<>();
        private final Map<PsiFile, Set<SliccMachineDecl>> machines = new HashMap<>();

        private ScopeData(PsiFile file) {
            files.add(file);
            files.addAll(SliccProtocolIndex.allMachineFilesContaining(file));
            files.addAll(SliccProtocolIndex.groupContaining(file));
            files.addAll(SliccProtocolIndex.globalFiles(file.getProject()));
            for (PsiFile candidate : new ArrayList<>(files)) {
                for (SliccMachineDecl machine : PsiTreeUtil.collectElementsOfType(candidate, SliccMachineDecl.class)) {
                    ProgressManager.checkCanceled();
                    Set<PsiFile> included = new LinkedHashSet<>();
                    for (SliccIncludeDecl include : PsiTreeUtil.collectElementsOfType(candidate, SliccIncludeDecl.class)) {
                        SliccMachineDecl owner = PsiTreeUtil.getParentOfType(include, SliccMachineDecl.class);
                        if (owner != null && !equivalent(machine, owner)) continue;
                        String path = include.getIncludedPath();
                        PsiFile target = path == null ? null : SliccProtocolIndex.resolveRelative(candidate, path);
                        if (target != null) SliccProtocolIndex.collectFromFile(target, included, new HashSet<>());
                    }
                    for (PsiFile includedFile : included) {
                        machines.computeIfAbsent(includedFile, unused -> new LinkedHashSet<>()).add(machine);
                    }
                    files.addAll(included);
                }
            }
        }
    }

    private static final class Scope {
        private final Set<PsiFile> files;
        private final Map<PsiFile, Set<SliccMachineDecl>> machines;
        private final PsiElement context;

        private Scope(PsiElement context) {
            this.context = context;
            PsiFile file = context.getContainingFile();
            ScopeData data = CachedValuesManager.getCachedValue(file, SCOPE_CACHE, () ->
                    CachedValueProvider.Result.create(new ScopeData(file), PsiModificationTracker.MODIFICATION_COUNT));
            files = data.files;
            machines = data.machines;
        }

        private Set<SliccMachineDecl> owners(PsiElement element) {
            SliccMachineDecl machine = PsiTreeUtil.getParentOfType(element, SliccMachineDecl.class);
            return machine != null ? Set.of(machine) : machines.getOrDefault(element.getContainingFile(), Set.of());
        }

        private boolean sameOwner(PsiElement first, PsiElement second) {
            PsiElement firstType = typeOwner(first);
            PsiElement secondType = typeOwner(second);
            if (firstType != null || secondType != null) return equivalent(firstType, secondType);
            Set<SliccMachineDecl> firstOwners = owners(first);
            Set<SliccMachineDecl> secondOwners = owners(second);
            if (firstOwners.isEmpty() || secondOwners.isEmpty()) return firstOwners.isEmpty() && secondOwners.isEmpty();
            return firstOwners.stream().anyMatch(owner -> secondOwners.stream().anyMatch(other -> equivalent(owner, other)));
        }

        private int rank(PsiElement candidate) {
            Set<SliccMachineDecl> contextOwners = owners(context);
            Set<SliccMachineDecl> candidateOwners = owners(candidate);
            if (candidateOwners.stream().anyMatch(owner -> contextOwners.stream()
                    .anyMatch(other -> equivalent(owner, other)))) return 0;
            return candidateOwners.isEmpty() ? 1 : -1;
        }

        private List<SliccFunctionDecl> freeFunctions(String name) {
            int bestRank = Integer.MAX_VALUE;
            List<SliccFunctionDecl> result = new ArrayList<>();
            for (PsiFile file : files) {
                for (SliccFunctionDecl function : PsiTreeUtil.collectElementsOfType(file, SliccFunctionDecl.class)) {
                    if (!name.equals(function.getName()) || typeOwner(function) != null) continue;
                    int rank = rank(function);
                    if (rank < 0 || rank > bestRank) continue;
                    if (rank < bestRank) {
                        result.clear();
                        bestRank = rank;
                    }
                    result.add(function);
                }
            }
            return result;
        }

        private List<SliccFunctionDecl> members(String type, String name, Set<String> visited) {
            if (!visited.add(type)) return List.of();
            PsiElement selected = null;
            int bestRank = Integer.MAX_VALUE;
            for (PsiFile file : files) {
                for (SliccNamedElement candidate : PsiTreeUtil.collectElementsOfType(file, SliccNamedElement.class)) {
                    if (!(candidate instanceof SliccStructureDecl || candidate instanceof SliccExternalTypeDecl)
                            || !type.equals(candidate.getName())) continue;
                    int rank = rank(candidate);
                    if (rank >= 0 && rank < bestRank) {
                        selected = candidate;
                        bestRank = rank;
                    }
                }
            }
            if (selected == null) return List.of();
            List<SliccFunctionDecl> result = directMembers(selected, name);
            if (!result.isEmpty()) return result;
            if (selected instanceof SliccStructureDecl structure) {
                String parent = SliccGotoDeclarationHandler.getStructureInterface(structure);
                if (parent != null) return members(parent, name, visited);
            }
            return List.of();
        }
    }
}
