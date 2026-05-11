package com.slicc.plugin.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.lang.SliccLanguage;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SliccGotoDeclarationHandler implements GotoDeclarationHandler {

    // ── Overload resolution helpers ───────────────────────────────────────

    /**
     * Extracts the argument expressions from a call site as dot-chains.
     * For `foo(a, b.x, c)` returns ["a", "b.x", "c"].
     * For `foo(tbe.checkpoint, addr)` returns ["tbe.checkpoint", "addr"].
     * Returns null if the identifier is not followed by '('.
     */
    @Nullable
    private static List<String> extractCallArgTokens(PsiElement callName) {
        com.intellij.lang.ASTNode node = callName.getNode();
        com.intellij.lang.ASTNode next = node.getTreeNext();
        while (next != null && next.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE)
            next = next.getTreeNext();
        if (next == null || next.getElementType() != SliccTokenTypes.OP_LPAREN) return null;

        List<String> args = new ArrayList<>();
        next = next.getTreeNext(); // skip '('
        int depth = 0;
        StringBuilder chain = new StringBuilder();
        boolean expectDotIdent = false;
        while (next != null) {
            IElementType t = next.getElementType();
            if (t == com.intellij.psi.TokenType.WHITE_SPACE) { next = next.getTreeNext(); continue; }
            if (t == SliccTokenTypes.OP_RPAREN && depth == 0) {
                args.add(chain.length() > 0 ? chain.toString() : null);
                break;
            }
            if (t == SliccTokenTypes.OP_LPAREN || t == SliccTokenTypes.OP_LBRACE
                    || t == SliccTokenTypes.OP_LBRACKET) { depth++; expectDotIdent = false; }
            else if (t == SliccTokenTypes.OP_RPAREN || t == SliccTokenTypes.OP_RBRACE
                    || t == SliccTokenTypes.OP_RBRACKET) depth--;
            else if (t == SliccTokenTypes.OP_COMMA && depth == 0) {
                args.add(chain.length() > 0 ? chain.toString() : null);
                chain = new StringBuilder();
                expectDotIdent = false;
            } else if (depth == 0) {
                if (t == SliccTokenTypes.KW_NEW) {
                    // `new TypeName` — next identifier is the type; prime chain to accept it
                    chain = new StringBuilder("new");
                } else if (t == SliccTokenTypes.IDENTIFIER) {
                    if (chain.length() == 0) {
                        chain.append(next.getText());
                    } else if (expectDotIdent) {
                        chain.append(next.getText());
                        expectDotIdent = false;
                    } else if ("new".equals(chain.toString())) {
                        // `new TypeName` — the type name is the argument type
                        chain = new StringBuilder(next.getText());
                    }
                    // other second identifiers without preceding dot — ignore (e.g. nested call)
                } else if (t == SliccTokenTypes.OP_DOT && chain.length() > 0 && !expectDotIdent) {
                    chain.append('.');
                    expectDotIdent = true;
                } else {
                    expectDotIdent = false;
                }
            }
            next = next.getTreeNext();
        }
        return args;
    }

    /**
     * Returns the declared parameter type names of a function declaration, in order.
     * Each entry is the text of the TYPE_REF's IDENTIFIER child (e.g. "Addr", "AbstractCacheEntry").
     */
    private static List<String> getDeclParamTypes(SliccFunctionDecl fn) {
        com.intellij.lang.ASTNode paramList = fn.getNode().findChildByType(SliccTypes.PARAM_LIST);
        if (paramList == null) return List.of();
        List<String> types = new ArrayList<>();
        for (com.intellij.lang.ASTNode child = paramList.getFirstChildNode();
             child != null; child = child.getTreeNext()) {
            if (child.getElementType() != SliccTypes.FIELD_DECL) continue;
            com.intellij.lang.ASTNode tr = child.findChildByType(SliccTypes.TYPE_REF);
            if (tr != null) {
                com.intellij.lang.ASTNode id = tr.findChildByType(SliccTokenTypes.IDENTIFIER);
                types.add(id != null ? id.getText() : null);
            } else {
                types.add(null);
            }
        }
        return types;
    }

    /**
     * Scores how well a function overload matches the call-site arguments and expected return type.
     *   +2 per argument with exact type match
     *   +1 per argument with compatible (subtype) match or unresolvable type
     *   +2 return type exact match bonus
     *   +1 return type compatible match bonus
     *   -1 if parameter count doesn't match
     *    0 if argTypes is null/empty (no info available)
     */
    private static int scoreOverload(SliccFunctionDecl fn, List<String> argTypes,
                                     PsiElement callSite) {
        List<String> paramTypes = getDeclParamTypes(fn);
        if (argTypes == null) return 0;
        if (paramTypes.size() != argTypes.size()) return -1;
        if (argTypes.isEmpty()) return 1; // zero-arg exact match

        PsiFile file = callSite.getContainingFile();
        int score = 0;

        // Score each argument
        for (int i = 0; i < argTypes.size(); i++) {
            String argExpr = argTypes.get(i);
            String paramType = paramTypes.get(i);
            if (argExpr == null || paramType == null) {
                score += 1; // count matches, type unknown
                continue;
            }
            String argType = resolveArgType(argExpr, callSite, file);
            if (argType == null) {
                score += 1; // count matches, type unresolvable
            } else if (argType.equals(paramType)) {
                score += 2; // exact type match
            } else if (isSubtype(argType, paramType, file)) {
                score += 1; // compatible (subtype) match
            }
            // else: type mismatch — 0 points
        }

        // Bonus: score return type against the expected type from the assignment LHS
        String expectedReturn = extractExpectedReturnType(callSite);
        if (expectedReturn != null) {
            String declReturn = getDeclReturnType(fn);
            if (declReturn != null) {
                if (declReturn.equals(expectedReturn)) {
                    score += 2;
                } else if (isSubtype(declReturn, expectedReturn, file)) {
                    score += 1;
                }
            }
        }

        return score;
    }

    /** Returns the declared return type name of a function (the first TYPE_REF child). */
    @Nullable
    private static String getDeclReturnType(SliccFunctionDecl fn) {
        com.intellij.lang.ASTNode typeRef = fn.getNode().findChildByType(SliccTypes.TYPE_REF);
        if (typeRef == null) return null;
        com.intellij.lang.ASTNode id = typeRef.findChildByType(SliccTokenTypes.IDENTIFIER);
        return id != null ? id.getText() : null;
    }

    /**
     * Extracts the expected return type from the assignment context of a call site.
     * For `VersionInfo v := cache.lookup(...)` returns "VersionInfo".
     * Looks for an enclosing FIELD_DECL that has an OP_ASSIGN token.
     */
    @Nullable
    private static String extractExpectedReturnType(PsiElement callSite) {
        PsiElement cur = callSite.getParent();
        while (cur != null && !(cur instanceof PsiFile)) {
            if (cur instanceof SliccFieldDecl) {
                boolean hasAssign = false;
                for (com.intellij.lang.ASTNode n = cur.getNode().getFirstChildNode();
                     n != null; n = n.getTreeNext()) {
                    if (n.getElementType() == SliccTokenTypes.OP_ASSIGN) { hasAssign = true; break; }
                }
                if (hasAssign) {
                    com.intellij.lang.ASTNode typeRef = cur.getNode().findChildByType(SliccTypes.TYPE_REF);
                    if (typeRef != null) {
                        com.intellij.lang.ASTNode id = typeRef.findChildByType(SliccTokenTypes.IDENTIFIER);
                        return id != null ? id.getText() : null;
                    }
                }
                return null;
            }
            cur = cur.getParent();
        }
        return null;
    }

    /**
     * Returns true if childType is the same as or a subtype of parentType,
     * following the 'interface' attribute chain in structure declarations.
     * e.g. Entry → AbstractCacheEntry (via interface = "AbstractCacheEntry").
     */
    private static boolean isSubtype(String childType, String parentType, PsiFile file) {
        if (childType == null || parentType == null) return false;
        if (childType.equals(parentType)) return true;
        Set<String> visited = new java.util.HashSet<>();
        String current = childType;
        while (current != null && visited.add(current)) {
            String iface = findInterfaceOf(current, file);
            if (iface == null) break;
            if (iface.equals(parentType)) return true;
            current = iface;
        }
        return false;
    }

    /** Finds the 'interface' attribute value of a structure declaration by name. */
    @Nullable
    private static String findInterfaceOf(String typeName, PsiFile file) {
        for (PsiFile f : collectSearchFiles(file)) {
            for (SliccStructureDecl s : PsiTreeUtil.collectElementsOfType(f, SliccStructureDecl.class)) {
                if (typeName.equals(s.getName())) {
                    String iface = getStructureInterface(s);
                    if (iface != null) return iface;
                }
            }
        }
        try {
            for (PsiFile f : SliccProtocolIndex.allProjectFiles(file.getProject())) {
                for (SliccStructureDecl s : PsiTreeUtil.collectElementsOfType(f, SliccStructureDecl.class)) {
                    if (typeName.equals(s.getName())) {
                        String iface = getStructureInterface(s);
                        if (iface != null) return iface;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Resolves the type of an argument expression (possibly a dot-chain like "tbe.checkpoint").
     * Splits on '.' and resolves each step: first part via implicit/message/var lookup,
     * subsequent parts via struct member type lookup.
     */
    @Nullable
    private static String resolveArgType(String argExpr, PsiElement callSite, PsiFile file) {
        if (argExpr == null || file == null) return null;
        String[] parts = argExpr.split("\\.");
        String type = resolveSimpleArgType(parts[0], callSite, file);
        for (int i = 1; i < parts.length && type != null; i++) {
            type = resolveMemberType(type, parts[i], file);
        }
        return type;
    }

    /** Resolves a simple (non-dot) identifier to its type. */
    @Nullable
    private static String resolveSimpleArgType(String argName, PsiElement callSite, PsiFile file) {
        if (isInActionBody(callSite)) {
            String implicit = IMPLICIT_VAR_TYPES.get(argName);
            if (implicit != null) return implicit;
        }
        String msgType = resolveMessageVarType(argName, callSite);
        if (msgType != null) return msgType;
        String varType = resolveVarType(argName, file);
        if (varType != null) return varType;
        // If the name is itself a type (e.g. `new Entry` → argName="Entry"), return it directly
        if (isTypeName(argName, file)) return argName;
        return null;
    }

    /** Returns true if name is declared as a structure or external_type in any reachable file. */
    private static boolean isTypeName(String name, PsiFile file) {
        for (PsiFile f : collectSearchFiles(file)) {
            for (SliccStructureDecl s : PsiTreeUtil.collectElementsOfType(f, SliccStructureDecl.class)) {
                if (name.equals(s.getName())) return true;
            }
            for (SliccExternalTypeDecl s : PsiTreeUtil.collectElementsOfType(f, SliccExternalTypeDecl.class)) {
                if (name.equals(s.getName())) return true;
            }
        }
        return false;
    }

    /**
     * From a list of candidates with the same name, picks the best match by
     * argument type matching, falling back to count-only, then first candidate.
     */
    @Nullable
    private static PsiElement pickBestFunction(List<SliccFunctionDecl> candidates,
                                               List<String> argTypes, PsiElement callSite) {
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) {
            PsiElement id = candidates.get(0).getNameIdentifier();
            return id != null ? id : candidates.get(0);
        }
        if (argTypes == null) {
            PsiElement id = candidates.get(0).getNameIdentifier();
            return id != null ? id : candidates.get(0);
        }

        SliccFunctionDecl best = null;
        int bestScore = Integer.MIN_VALUE;
        for (SliccFunctionDecl fn : candidates) {
            int score = scoreOverload(fn, argTypes, callSite);
            if (score > bestScore) {
                bestScore = score;
                best = fn;
            }
        }
        if (best == null || bestScore < 0) {
            // All candidates had wrong count — fall back to first
            best = candidates.get(0);
        }
        PsiElement id = best.getNameIdentifier();
        return id != null ? id : best;
    }

    /** Counts the number of arguments (for the argCount-only path). */
    private static int countCallArguments(PsiElement callName) {
        List<String> args = extractCallArgTokens(callName);
        if (args == null) return -1;
        return args.size();
    }

    // ── Public API for completion ─────────────────────────────────────────

    /**
     * Given the ASTNode of the identifier before a dot, returns its resolved type name
     * (e.g. "TBE", "InternalMsg"), or null if the type cannot be determined.
     */
    @Nullable
    public static String resolveOwnerTypeForCompletion(
            @org.jetbrains.annotations.NotNull com.intellij.lang.ASTNode ownerNode,
            @org.jetbrains.annotations.NotNull PsiElement context,
            @org.jetbrains.annotations.Nullable PsiFile originalFile) {
        return resolveNodeType(ownerNode, context, originalFile);
    }

    // Keep old overload for non-completion callers
    public static String resolveOwnerTypeForCompletion(
            @org.jetbrains.annotations.NotNull com.intellij.lang.ASTNode ownerNode,
            @org.jetbrains.annotations.NotNull PsiElement context) {
        return resolveNodeType(ownerNode, context, null);
    }

    /**
     * Returns all SliccFunctionDecl overloads with the given name visible from {@code file}.
     * Searches machine scope, protocol group, global scope, and all project files.
     */
    @org.jetbrains.annotations.NotNull
    public static List<SliccFunctionDecl> collectFunctionOverloads(
            @org.jetbrains.annotations.NotNull String name,
            @org.jetbrains.annotations.NotNull PsiFile file) {
        List<SliccFunctionDecl> result = new ArrayList<>();
        List<PsiFile> searchFiles = collectSearchFiles(file);
        try { searchFiles.addAll(SliccProtocolIndex.allProjectFiles(file.getProject())); }
        catch (Exception ignored) {}
        java.util.Set<PsiFile> seen = new java.util.LinkedHashSet<>(searchFiles);
        for (PsiFile f : seen) {
            for (SliccFunctionDecl fn : PsiTreeUtil.collectElementsOfType(f, SliccFunctionDecl.class)) {
                if (!name.equals(fn.getName())) continue;
                // Only top-level functions (not member functions inside structure/external_type)
                PsiElement parent = fn.getParent();
                if (parent instanceof SliccStructureDecl || parent instanceof SliccExternalTypeDecl) continue;
                result.add(fn);
            }
        }
        return result;
    }

    /**
     * Returns all SliccFunctionDecl overloads with the given name that are members of
     * the structure/external_type named {@code typeName}.
     */
    @org.jetbrains.annotations.NotNull
    public static List<SliccFunctionDecl> collectMemberFunctionOverloads(
            @org.jetbrains.annotations.NotNull String typeName,
            @org.jetbrains.annotations.NotNull String funcName,
            @org.jetbrains.annotations.NotNull PsiFile file) {
        List<SliccFunctionDecl> result = new ArrayList<>();
        java.util.Set<PsiFile> seen = new java.util.LinkedHashSet<>(collectSearchFiles(file));
        try { seen.addAll(SliccProtocolIndex.allProjectFiles(file.getProject())); }
        catch (Exception ignored) {}
        for (PsiFile f : seen) {
            for (SliccStructureDecl s : PsiTreeUtil.collectElementsOfType(f, SliccStructureDecl.class)) {
                if (!typeName.equals(s.getName())) continue;
                for (PsiElement ch : s.getChildren()) {
                    if (ch instanceof SliccFunctionDecl fn && funcName.equals(fn.getName()))
                        result.add(fn);
                }
            }
            for (SliccExternalTypeDecl s : PsiTreeUtil.collectElementsOfType(f, SliccExternalTypeDecl.class)) {
                if (!typeName.equals(s.getName())) continue;
                for (PsiElement ch : s.getChildren()) {
                    if (ch instanceof SliccFunctionDecl fn && funcName.equals(fn.getName()))
                        result.add(fn);
                }
            }
        }
        return result;
    }

    /**
     * named {@code typeName}, following the interface inheritance chain.
     * Deduplicates by name (first declaration wins).
     */
    @org.jetbrains.annotations.NotNull
    public static List<SliccNamedElement> collectMembersOfType(
            @org.jetbrains.annotations.NotNull String typeName,
            @org.jetbrains.annotations.NotNull PsiFile file) {
        List<SliccNamedElement> result = new ArrayList<>();
        java.util.Set<String> seenNames = new java.util.LinkedHashSet<>();
        java.util.Set<String> visitedTypes = new java.util.HashSet<>();
        collectMembersRecursive(typeName, file, result, seenNames, visitedTypes);
        return result;
    }

    private static void collectMembersRecursive(String typeName, PsiFile file,
            List<SliccNamedElement> result, java.util.Set<String> seenNames,
            java.util.Set<String> visitedTypes) {
        if (!visitedTypes.add(typeName)) return;
        List<PsiFile> searchFiles = new ArrayList<>(collectSearchFiles(file));
        try { searchFiles.addAll(SliccProtocolIndex.allProjectFiles(file.getProject())); }
        catch (Exception ignored) {}

        String ifaceToFollow = null;
        for (PsiFile f : searchFiles) {
            for (SliccStructureDecl s : PsiTreeUtil.collectElementsOfType(f, SliccStructureDecl.class)) {
                if (!typeName.equals(s.getName())) continue;
                // Only direct children — avoids picking up function parameters (also SliccFieldDecl)
                for (PsiElement ch : s.getChildren()) {
                    if (ch instanceof SliccFieldDecl field) {
                        String n = field.getName();
                        if (n != null && seenNames.add(n)) result.add(field);
                    } else if (ch instanceof SliccFunctionDecl fn) {
                        String n = fn.getName();
                        if (n != null && seenNames.add(n)) result.add(fn);
                    }
                }
                if (ifaceToFollow == null) ifaceToFollow = getStructureInterface(s);
            }
            for (SliccExternalTypeDecl s : PsiTreeUtil.collectElementsOfType(f, SliccExternalTypeDecl.class)) {
                if (!typeName.equals(s.getName())) continue;
                for (PsiElement ch : s.getChildren()) {
                    if (ch instanceof SliccFunctionDecl fn) {
                        String n = fn.getName();
                        if (n != null && seenNames.add(n)) result.add(fn);
                    }
                }
            }
        }
        if (ifaceToFollow != null)
            collectMembersRecursive(ifaceToFollow, file, result, seenNames, visitedTypes);
    }

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(
            @Nullable PsiElement sourceElement,
            int offset,
            Editor editor) {

        if (sourceElement == null) return null;
        if (!sourceElement.getLanguage().isKindOf(SliccLanguage.INSTANCE)) return null;

        IElementType tokenType = sourceElement.getNode().getElementType();
        // Built-in statement keywords (set_tbe, set_cache_entry, etc.) may also have
        // user-declared function counterparts — navigate to those if found.
        boolean isBuiltinKeyword = isBuiltinStmtKeyword(tokenType);
        if (tokenType != SliccTokenTypes.IDENTIFIER && !isBuiltinKeyword) return null;

        // Built-in keyword tokens skip declaration-self / implicit-var / dot-access logic;
        // they only navigate to user-declared function counterparts.
        if (isBuiltinKeyword) {
            String key = sourceElement.getText();
            PsiFile file = sourceElement.getContainingFile();
            if (file == null) return null;
            return findFunctionInAllScopes(key, file);
        }

        // If clicking on a declaration's own name, navigate to self.
        // Machine names (e.g. SpPCache) are special: they reference an enum member, so search further.
        SliccNamedElement ownDecl = findOwnDeclaration(sourceElement);
        if (ownDecl != null && !(ownDecl instanceof SliccMachineDecl)) {
            return new PsiElement[]{sourceElement};
        }

        String key = sourceElement.getText();
        PsiFile file = sourceElement.getContainingFile();
        if (file == null) return null;

        boolean isTypeRef = isInTypeRefPosition(sourceElement);
        boolean isDotAccess = isDotAccess(sourceElement);
        boolean isColonAccess = isColonAccess(sourceElement);

        // Implicit built-in variables in ACTION bodies only → navigate to type declaration
        if (!isDotAccess && !isColonAccess) {
            String implicitType = getImplicitVarType(key, sourceElement);
            if (implicitType != null) {
                PsiElement typeTarget = findTypeInAllScopes(implicitType, file);
                if (typeTarget != null) return new PsiElement[]{typeTarget};
            }
        }

        // Dot-access: resolve the type of the object before '.' for precise member lookup
        if (isDotAccess) {
            String ownerType = resolveDotOwnerType(sourceElement);
            if (ownerType != null) {
                List<String> dotArgTypes = extractCallArgTokens(sourceElement);
                PsiElement member = findMemberInAllScopes(ownerType, key, file, dotArgTypes, sourceElement);
                if (member != null) return new PsiElement[]{member};
                return null;
            }
        }

        // Colon-access: resolve enum member (e.g. MessageSizeType:Data, SpMsgType:Inv)
        if (isColonAccess) {
            String enumType = resolveColonOwnerType(sourceElement);
            if (enumType != null) {
                PsiElement member = findEnumMemberInAllScopes(enumType, key, file);
                if (member != null) return new PsiElement[]{member};
                return null;
            }
        }

        // Step 0: local scope — SliccFieldDecl declared in the enclosing action/in_port body
        // (e.g. `Addr address := in_msg.addr;` takes priority over Entry.address struct field)
        PsiElement localResult = searchLocalScope(sourceElement, key);
        if (localResult != null) return new PsiElement[]{localResult};

        List<String> argTypes = isTypeRef ? null : extractCallArgTokens(sourceElement);

        // Step 1: current file
        PsiElement result = searchInFile(file, key, isTypeRef, isDotAccess, sourceElement, "self", argTypes, sourceElement);
        if (result != null) return new PsiElement[]{result};

        // Step 2: machine scope
        try {
            for (PsiFile f : SliccProtocolIndex.machineFilesContaining(file)) {
                result = searchInFile(f, key, isTypeRef, isDotAccess, sourceElement, "machine", argTypes, sourceElement);
                if (result != null) return new PsiElement[]{result};
            }
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[SLICC-GOTO] machine scope error: " + e);
        }

        // Step 3: full protocol group
        try {
            for (PsiFile f : SliccProtocolIndex.groupContaining(file)) {
                result = searchInFile(f, key, isTypeRef, isDotAccess, sourceElement, "protocol", argTypes, sourceElement);
                if (result != null) return new PsiElement[]{result};
            }
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[SLICC-GOTO] group error: " + e);
        }

        // Step 4: global scope
        try {
            for (PsiFile f : SliccProtocolIndex.globalFiles(file.getProject())) {
                result = searchInFile(f, key, isTypeRef, isDotAccess, sourceElement, "global", argTypes, sourceElement);
                if (result != null) return new PsiElement[]{result};
            }
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[SLICC-GOTO] global error: " + e);
        }

        // Step 5: all project .sm/.slicc files — last resort for declarations not in include chain
        try {
            for (PsiFile f : SliccProtocolIndex.allProjectFiles(file.getProject())) {
                result = searchInFile(f, key, isTypeRef, isDotAccess, sourceElement, "project", argTypes, sourceElement);
                if (result != null) return new PsiElement[]{result};
            }
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[SLICC-GOTO] project error: " + e);
        }

        return null;
    }

    /** Walk ancestors to find a SliccNamedElement whose getNameIdentifier() == element. */
    @Nullable
    private static SliccNamedElement findOwnDeclaration(PsiElement element) {
        PsiElement cur = element.getParent();
        while (cur != null && !(cur instanceof PsiFile)) {
            if (cur instanceof SliccNamedElement named && element.equals(named.getNameIdentifier())) {
                return named;
            }
            cur = cur.getParent();
        }
        return null;
    }

    private static boolean isBuiltinStmtKeyword(IElementType t) {
        return t == SliccTokenTypes.KW_CHECK_ALLOCATE
                || t == SliccTokenTypes.KW_TRIGGER
                || t == SliccTokenTypes.KW_STATIC_CAST
                || t == SliccTokenTypes.KW_IS_VALID || t == SliccTokenTypes.KW_IS_INVALID;
    }

    @Nullable
    private static PsiElement[] findFunctionInAllScopes(String name, PsiFile file) {
        List<SliccFunctionDecl> candidates = new ArrayList<>();
        for (PsiFile f : collectSearchFiles(file)) {
            for (SliccFunctionDecl fn : PsiTreeUtil.collectElementsOfType(f, SliccFunctionDecl.class)) {
                if (name.equals(fn.getName())) candidates.add(fn);
            }
        }
        if (candidates.isEmpty()) return null;
        // No call-site element available here (builtin keyword path) — return first match
        PsiElement id = candidates.get(0).getNameIdentifier();
        return new PsiElement[]{id != null ? id : candidates.get(0)};
    }

    private static boolean isInTypeRefPosition(PsiElement element) {
        PsiElement parent = element.getParent();
        return parent instanceof SliccCompositeElement p
                && p.getNode().getElementType() == SliccTypes.TYPE_REF;
    }

    /** Returns true if this identifier is preceded by a '.' (member access like cache.invalidate). */
    private static boolean isDotAccess(PsiElement element) {
        com.intellij.lang.ASTNode node = element.getNode();
        com.intellij.lang.ASTNode prev = node.getTreePrev();
        while (prev != null && prev.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE) {
            prev = prev.getTreePrev();
        }
        return prev != null && prev.getElementType() == SliccTokenTypes.OP_DOT;
    }

    /** Returns true if this identifier is preceded by ':' which is preceded by an identifier
     *  (enum qualified access like MessageSizeType:Data). */
    private static boolean isColonAccess(PsiElement element) {
        com.intellij.lang.ASTNode node = element.getNode();
        com.intellij.lang.ASTNode prev = node.getTreePrev();
        while (prev != null && prev.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE)
            prev = prev.getTreePrev();
        if (prev == null || prev.getElementType() != SliccTokenTypes.OP_COLON) return false;
        com.intellij.lang.ASTNode beforeColon = prev.getTreePrev();
        while (beforeColon != null && beforeColon.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE)
            beforeColon = beforeColon.getTreePrev();
        return beforeColon != null && beforeColon.getElementType() == SliccTokenTypes.IDENTIFIER;
    }

    @Nullable
    private static String resolveColonOwnerType(PsiElement element) {
        com.intellij.lang.ASTNode colon = element.getNode().getTreePrev();
        while (colon != null && colon.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE)
            colon = colon.getTreePrev();
        if (colon == null || colon.getElementType() != SliccTokenTypes.OP_COLON) return null;
        com.intellij.lang.ASTNode owner = colon.getTreePrev();
        while (owner != null && owner.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE)
            owner = owner.getTreePrev();
        return (owner != null && owner.getElementType() == SliccTokenTypes.IDENTIFIER)
                ? owner.getText() : null;
    }

    @Nullable
    private static PsiElement findEnumMemberInAllScopes(String enumType, String memberName, PsiFile file) {
        for (PsiFile f : collectSearchFiles(file)) {
            PsiElement m = findEnumMemberInFile(f, enumType, memberName);
            if (m != null) return m;
        }
        return null;
    }

    @Nullable
    private static PsiElement findEnumMemberInFile(PsiFile file, String enumType, String memberName) {
        for (SliccEnumerationDecl e : PsiTreeUtil.collectElementsOfType(file, SliccEnumerationDecl.class)) {
            if (!enumType.equals(e.getName())) continue;
            for (SliccEnumMember m : PsiTreeUtil.collectElementsOfType(e, SliccEnumMember.class)) {
                if (memberName.equals(m.getName())) {
                    PsiElement id = m.getNameIdentifier(); return id != null ? id : m;
                }
            }
        }
        for (SliccStateDecl s : PsiTreeUtil.collectElementsOfType(file, SliccStateDecl.class)) {
            if (!enumType.equals(s.getName())) continue;
            for (SliccEnumMember m : PsiTreeUtil.collectElementsOfType(s, SliccEnumMember.class)) {
                if (memberName.equals(m.getName())) {
                    PsiElement id = m.getNameIdentifier(); return id != null ? id : m;
                }
            }
        }
        return null;
    }

    @Nullable
    private static PsiElement searchInFile(PsiFile file, String key, boolean isTypeRef,
                                           boolean isDotAccess, PsiElement skip, String scope,
                                           List<String> argTypes, PsiElement callSite) {
        String fname = file.getVirtualFile() != null ? file.getVirtualFile().getName() : file.getName();
        try {
            List<SliccNamedElement> decls = isTypeRef
                    ? collectTypeDeclarations(file)
                    : collectAllDeclarations(file, isDotAccess);
            List<SliccFunctionDecl> fnCandidates = null;
            for (SliccNamedElement decl : decls) {
                String name;
                try {
                    name = decl.getName();
                } catch (ProcessCanceledException e) {
                    throw e;
                } catch (Exception e) {
                    continue;
                }
                if (!key.equals(name)) continue;
                if (decl instanceof SliccFunctionDecl fn) {
                    if (fnCandidates == null) fnCandidates = new ArrayList<>();
                    fnCandidates.add(fn);
                    continue;
                }
                PsiElement nameId = decl.getNameIdentifier();
                PsiElement target = nameId != null ? nameId : decl;
                if (target == skip) continue;
                return target;
            }
            if (fnCandidates != null) {
                PsiElement cs = callSite != null ? callSite : skip;
                PsiElement target = pickBestFunction(fnCandidates, argTypes, cs);
                if (target != null && target != skip) {
                    return target;
                }
            }
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[SLICC-GOTO] exception in " + fname + ": " + e);
        }
        return null;
    }

    private static List<SliccNamedElement> collectTypeDeclarations(PsiFile file) {
        List<SliccNamedElement> result = new ArrayList<>();
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccStructureDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccExternalTypeDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccEnumerationDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccStateDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccMachineDecl.class));
        return result;
    }

    private static List<SliccNamedElement> collectAllDeclarations(PsiFile file, boolean includeMemberFunctions) {
        List<SliccNamedElement> result = new ArrayList<>();
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccStructureDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccExternalTypeDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccEnumerationDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccStateDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccMachineDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccActionDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccInPortDecl.class));
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccOutPortDecl.class));
        // Only include machine-level fields (not local vars/params, and not struct members).
        // Struct member fields are only reachable via dot access and are resolved by findMemberInAllScopes.
        for (SliccFieldDecl f : PsiTreeUtil.collectElementsOfType(file, SliccFieldDecl.class)) {
            if (isStructuralField(f) && isMachineLevelField(f)) result.add(f);
        }
        result.addAll(PsiTreeUtil.collectElementsOfType(file, SliccEnumMember.class));
        for (SliccFunctionDecl fn : PsiTreeUtil.collectElementsOfType(file, SliccFunctionDecl.class)) {
            if (includeMemberFunctions || isTopLevel(fn)) result.add(fn);
        }
        return result;
    }

    /** Returns true if this field is a struct/machine-level declaration (not a local var or param). */
    private static boolean isStructuralField(SliccFieldDecl field) {
        PsiElement cur = field.getParent();
        while (cur != null && !(cur instanceof PsiFile)) {
            if (cur instanceof SliccActionDecl
                    || cur instanceof SliccInPortDecl
                    || cur instanceof SliccFunctionDecl) return false;
            cur = cur.getParent();
        }
        return true;
    }

    /** Returns true if this field is at machine level (not inside a structure or external_type body).
     *  Fields inside 'global { }' blocks are allowed — they are referenced directly by name. */
    private static boolean isMachineLevelField(SliccFieldDecl field) {
        PsiElement cur = field.getParent();
        while (cur != null && !(cur instanceof PsiFile)) {
            if (cur instanceof SliccStructureDecl) {
                // 'global { }' fields are accessible without dot notation — allow them
                com.intellij.lang.ASTNode first = cur.getNode().getFirstChildNode();
                if (first != null
                        && first.getElementType() == SliccTokenTypes.IDENTIFIER
                        && "global".equals(first.getText())) {
                    cur = cur.getParent();
                    continue;
                }
                return false;
            }
            if (cur instanceof SliccExternalTypeDecl) return false;
            cur = cur.getParent();
        }
        return true;
    }

    /** Returns true if decl has no SliccStructureDecl/SliccExternalTypeDecl ancestor within the file. */
    private static boolean isTopLevel(SliccNamedElement decl) {
        PsiElement cur = decl.getParent();
        while (cur != null && !(cur instanceof PsiFile)) {
            if (cur instanceof SliccStructureDecl || cur instanceof SliccExternalTypeDecl) return false;
            cur = cur.getParent();
        }
        return true;
    }

    private static final java.util.Map<String, String> IMPLICIT_VAR_TYPES = java.util.Map.of(
            "tbe",         "TBE",
            "cache_entry", "Entry",
            "address",     "Addr"
    );

    @Nullable
    private static String getImplicitVarType(String name, PsiElement element) {
        String typeName = IMPLICIT_VAR_TYPES.get(name);
        if (typeName == null) return null;
        return isInActionBody(element) ? typeName : null;
    }


    private static boolean isInActionBody(PsiElement element) {
        PsiElement cur = element.getParent();
        while (cur != null && !(cur instanceof PsiFile)) {
            if (cur instanceof SliccActionDecl) return true;
            cur = cur.getParent();
        }
        return false;
    }

    /**
     * For a dot-access like `tbe.pos` or `in_msg.list.count`, resolve the type of the
     * object before the dot. Handles chained access recursively.
     */
    @Nullable
    private static String resolveDotOwnerType(PsiElement element) {
        com.intellij.lang.ASTNode node = element.getNode();
        com.intellij.lang.ASTNode dot = node.getTreePrev();
        while (dot != null && dot.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE)
            dot = dot.getTreePrev();
        if (dot == null || dot.getElementType() != SliccTokenTypes.OP_DOT) return null;

        com.intellij.lang.ASTNode ownerNode = dot.getTreePrev();
        while (ownerNode != null && ownerNode.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE)
            ownerNode = ownerNode.getTreePrev();
        if (ownerNode == null || ownerNode.getElementType() != SliccTokenTypes.IDENTIFIER) return null;

        return resolveNodeType(ownerNode, element);
    }

    /**
     * Resolves the type of an identifier node, handling chained dot access recursively.
     * e.g. for `in_msg.list.count`, when resolving `list`:
     *   - sees `in_msg.list` → resolves `in_msg` as InternalMsg, then finds `list` field type
     */
    @Nullable
    private static String resolveNodeType(com.intellij.lang.ASTNode ownerNode, PsiElement contextElement) {
        return resolveNodeType(ownerNode, contextElement, null);
    }

    @Nullable
    private static String resolveNodeType(com.intellij.lang.ASTNode ownerNode, PsiElement contextElement,
                                           @org.jetbrains.annotations.Nullable PsiFile originalFile) {
        String ownerName = ownerNode.getText();

        // Prefer originalFile (real file with VirtualFile) over contextElement.getContainingFile()
        // which may be a dummy copy during completion.
        PsiFile effectiveFile = (originalFile != null && originalFile.getVirtualFile() != null)
                ? originalFile : contextElement.getContainingFile();

        // Check if ownerNode is itself a dot-access (chained: grandOwner.ownerNode)
        com.intellij.lang.ASTNode prevDot = ownerNode.getTreePrev();
        while (prevDot != null && prevDot.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE)
            prevDot = prevDot.getTreePrev();
        if (prevDot != null && prevDot.getElementType() == SliccTokenTypes.OP_DOT) {
            com.intellij.lang.ASTNode grandOwner = prevDot.getTreePrev();
            while (grandOwner != null && grandOwner.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE)
                grandOwner = grandOwner.getTreePrev();
            if (grandOwner != null && grandOwner.getElementType() == SliccTokenTypes.IDENTIFIER) {
                String grandOwnerType = resolveNodeType(grandOwner, contextElement, originalFile);
                if (grandOwnerType != null && effectiveFile != null)
                    return resolveMemberType(grandOwnerType, ownerName, effectiveFile);
                return null; // chain broken
            }
        }

        // Simple identifier — resolve via implicit vars, message vars, or field declarations
        if (isInActionBody(contextElement)) {
            String implicit = IMPLICIT_VAR_TYPES.get(ownerName);
            if (implicit != null) return implicit;
        }
        String msgType = resolveMessageVarType(ownerName, contextElement);
        if (msgType != null) return msgType;

        if (effectiveFile == null) return null;
        return resolveVarType(ownerName, effectiveFile);
    }

    /**
     * Returns the type produced by accessing {@code memberName} on a value of type {@code structType}.
     * Checks struct fields first, then member functions (returning their return type).
     * Follows the 'interface' inheritance chain if the member is not found directly.
     */
    @Nullable
    private static String resolveMemberType(String structType, String memberName, PsiFile file) {
        return resolveMemberType(structType, memberName, file, new java.util.HashSet<>());
    }

    @Nullable
    private static String resolveMemberType(String structType, String memberName, PsiFile file,
                                             Set<String> visited) {
        if (!visited.add(structType)) return null;
        List<PsiFile> searchFiles = new ArrayList<>(collectSearchFiles(file));
        try { searchFiles.addAll(SliccProtocolIndex.allProjectFiles(file.getProject())); }
        catch (Exception ignored) {}

        String ifaceToFollow = null;
        for (PsiFile f : searchFiles) {
            for (SliccStructureDecl s : PsiTreeUtil.collectElementsOfType(f, SliccStructureDecl.class)) {
                if (!structType.equals(s.getName())) continue;
                // Fields
                for (SliccFieldDecl field : PsiTreeUtil.collectElementsOfType(s, SliccFieldDecl.class)) {
                    if (memberName.equals(field.getName())) {
                        com.intellij.lang.ASTNode tr = field.getNode().findChildByType(SliccTypes.TYPE_REF);
                        if (tr != null) {
                            com.intellij.lang.ASTNode id = tr.findChildByType(SliccTokenTypes.IDENTIFIER);
                            if (id != null) return id.getText();
                        }
                    }
                }
                // Member functions — return their declared return type
                for (SliccFunctionDecl fn : PsiTreeUtil.collectElementsOfType(s, SliccFunctionDecl.class)) {
                    if (memberName.equals(fn.getName())) {
                        String ret = getDeclReturnType(fn);
                        if (ret != null) return ret;
                    }
                }
                // Record interface for chain follow (use first found)
                if (ifaceToFollow == null) ifaceToFollow = getStructureInterface(s);
            }
            for (SliccExternalTypeDecl s : PsiTreeUtil.collectElementsOfType(f, SliccExternalTypeDecl.class)) {
                if (!structType.equals(s.getName())) continue;
                for (SliccFunctionDecl fn : PsiTreeUtil.collectElementsOfType(s, SliccFunctionDecl.class)) {
                    if (memberName.equals(fn.getName())) {
                        String ret = getDeclReturnType(fn);
                        if (ret != null) return ret;
                    }
                }
            }
        }
        // Follow interface chain
        if (ifaceToFollow != null) return resolveMemberType(ifaceToFollow, memberName, file, visited);
        return null;
    }

    /**
     * Resolves the type of `in_msg` and `out_msg` from the enclosing peek/enqueue statement.
     *   peek(portName, MsgType)    { ... }  →  in_msg  : MsgType
     *   enqueue(portName, MsgType) { ... }  →  out_msg : MsgType
     * Walks up from element to find the enclosing BLOCK whose parent STATEMENT begins with
     * KW_PEEK or KW_ENQUEUE, then extracts the second identifier argument.
     */
    @Nullable
    private static String resolveMessageVarType(String varName, PsiElement element) {
        if (!varName.equals("in_msg") && !varName.equals("out_msg")) return null;
        PsiElement cur = element.getParent();
        while (cur != null && !(cur instanceof PsiFile)) {
            if (cur instanceof SliccCompositeElement
                    && cur.getNode().getElementType() == SliccTypes.BLOCK) {
                PsiElement stmtParent = cur.getParent();
                if (stmtParent != null) {
                    com.intellij.lang.ASTNode sn = stmtParent.getNode();
                    com.intellij.lang.ASTNode first = sn.getFirstChildNode();
                    while (first != null && first.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE)
                        first = first.getTreeNext();
                    if (first != null) {
                        boolean isPeek = first.getElementType() == SliccTokenTypes.KW_PEEK
                                && "in_msg".equals(varName);
                        boolean isEnqueue = first.getElementType() == SliccTokenTypes.KW_ENQUEUE
                                && "out_msg".equals(varName);
                        if (isPeek || isEnqueue) {
                            // skip to '('
                            com.intellij.lang.ASTNode n = first.getTreeNext();
                            while (n != null && n.getElementType() != SliccTokenTypes.OP_LPAREN)
                                n = n.getTreeNext();
                            if (n != null) n = n.getTreeNext(); // skip '('
                            // skip whitespace
                            while (n != null && n.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE)
                                n = n.getTreeNext();
                            // skip first arg (port name)
                            if (n != null && n.getElementType() == SliccTokenTypes.IDENTIFIER)
                                n = n.getTreeNext();
                            // skip whitespace and comma
                            while (n != null && (n.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE
                                    || n.getElementType() == SliccTokenTypes.OP_COMMA))
                                n = n.getTreeNext();
                            // second arg is the message type name
                            if (n != null && n.getElementType() == SliccTokenTypes.IDENTIFIER)
                                return n.getText();
                        }
                    }
                }
            }
            cur = cur.getParent();
        }
        return null;
    }

    /**
     * Finds the declared type of a variable by searching SliccFieldDecl with matching name.
     * Prefers machine-level fields (not inside struct bodies) over struct member fields,
     * so that e.g. `SpSequencer* sequencer` at machine level is found before any struct
     * member that happens to share the same name.
     * Returns the type name string (the text of its TYPE_REF child), or null.
     */
    @Nullable
    private static String resolveVarType(String varName, PsiFile file) {
        List<PsiFile> searchFiles = collectSearchFiles(file);

        // Pass 1: machine-level fields only (not inside struct/external_type bodies)
        for (PsiFile f : searchFiles) {
            for (SliccFieldDecl field : PsiTreeUtil.collectElementsOfType(f, SliccFieldDecl.class)) {
                if (!varName.equals(field.getName())) continue;
                if (!isMachineLevelField(field)) continue;
                String t = extractTypeFromField(field);
                if (t != null) return t;
            }
            for (SliccInPortDecl port : PsiTreeUtil.collectElementsOfType(f, SliccInPortDecl.class)) {
                if (varName.equals(port.getName())) {
                    com.intellij.lang.ASTNode typeRef = port.getNode().findChildByType(SliccTypes.TYPE_REF);
                    if (typeRef != null) {
                        com.intellij.lang.ASTNode typeId = typeRef.findChildByType(SliccTokenTypes.IDENTIFIER);
                        if (typeId != null) return typeId.getText();
                    }
                }
            }
        }

        // Pass 2: local vars / params / struct members (fallback)
        for (PsiFile f : searchFiles) {
            for (SliccFieldDecl field : PsiTreeUtil.collectElementsOfType(f, SliccFieldDecl.class)) {
                if (!varName.equals(field.getName())) continue;
                if (isMachineLevelField(field)) continue; // already checked
                String t = extractTypeFromField(field);
                if (t != null) return t;
            }
        }
        return null;
    }

    @Nullable
    private static String extractTypeFromField(SliccFieldDecl field) {
        com.intellij.lang.ASTNode typeRef = field.getNode().findChildByType(SliccTypes.TYPE_REF);
        if (typeRef == null) return null;
        com.intellij.lang.ASTNode typeId = typeRef.findChildByType(SliccTokenTypes.IDENTIFIER);
        return typeId != null ? typeId.getText() : null;
    }

    private static List<PsiFile> collectSearchFiles(PsiFile file) {
        List<PsiFile> files = new ArrayList<>();
        files.add(file);
        try { files.addAll(SliccProtocolIndex.machineFilesContaining(file)); } catch (Exception ignored) {}
        try { files.addAll(SliccProtocolIndex.groupContaining(file)); } catch (Exception ignored) {}
        try { files.addAll(SliccProtocolIndex.globalFiles(file.getProject())); } catch (Exception ignored) {}
        return files;
    }

    /** Find a named member (field or function) inside the structure/external_type named structType.
     *  If not found directly, follows the 'interface' attribute chain (e.g. SpSequencer → Sequencer). */
    @Nullable
    private static PsiElement findMemberInAllScopes(String structType, String memberName, PsiFile file) {
        return findMemberInAllScopes(structType, memberName, file, null, null);
    }

    @Nullable
    private static PsiElement findMemberInAllScopes(String structType, String memberName,
                                                     PsiFile file, List<String> argTypes,
                                                     PsiElement callSite) {
        return findMemberInAllScopes(structType, memberName, file, argTypes, callSite,
                new java.util.HashSet<>());
    }

    @Nullable
    private static PsiElement findMemberInAllScopes(String structType, String memberName,
                                                     PsiFile file, List<String> argTypes,
                                                     PsiElement callSite,
                                                     java.util.Set<String> visited) {
        if (!visited.add(structType)) return null; // prevent infinite loops
        List<PsiFile> searchFiles = collectSearchFiles(file);

        // Direct lookup
        for (PsiFile f : searchFiles) {
            PsiElement m = findMemberInFile(f, structType, memberName, argTypes, callSite);
            if (m != null) return m;
        }
        // Fallback: all project files
        try {
            for (PsiFile f : SliccProtocolIndex.allProjectFiles(file.getProject())) {
                PsiElement m = findMemberInFile(f, structType, memberName, argTypes, callSite);
                if (m != null) return m;
            }
        } catch (Exception ignored) {}

        // Follow interface chain: structure(SpSequencer, interface = "Sequencer", ...)
        String iface = null;
        outer:
        for (PsiFile f : searchFiles) {
            for (SliccStructureDecl s : PsiTreeUtil.collectElementsOfType(f, SliccStructureDecl.class)) {
                if (structType.equals(s.getName())) {
                    iface = getStructureInterface(s);
                    if (iface != null) break outer;
                }
            }
        }
        if (iface == null) {
            try {
                for (PsiFile f : SliccProtocolIndex.allProjectFiles(file.getProject())) {
                    for (SliccStructureDecl s : PsiTreeUtil.collectElementsOfType(f, SliccStructureDecl.class)) {
                        if (structType.equals(s.getName())) {
                            iface = getStructureInterface(s);
                            if (iface != null) break;
                        }
                    }
                    if (iface != null) break;
                }
            } catch (Exception ignored) {}
        }
        if (iface != null) return findMemberInAllScopes(iface, memberName, file, argTypes, callSite, visited);
        return null;
    }

    @Nullable
    private static PsiElement findMemberInFile(PsiFile file, String structType, String memberName) {
        return findMemberInFile(file, structType, memberName, null, null);
    }

    @Nullable
    private static PsiElement findMemberInFile(PsiFile file, String structType, String memberName,
                                               List<String> argTypes, PsiElement callSite) {
        for (SliccStructureDecl s : PsiTreeUtil.collectElementsOfType(file, SliccStructureDecl.class)) {
            if (!structType.equals(s.getName())) continue;
            for (SliccFieldDecl f : PsiTreeUtil.collectElementsOfType(s, SliccFieldDecl.class)) {
                if (memberName.equals(f.getName())) {
                    PsiElement id = f.getNameIdentifier(); return id != null ? id : f;
                }
            }
            List<SliccFunctionDecl> fnCandidates = new ArrayList<>();
            for (SliccFunctionDecl f : PsiTreeUtil.collectElementsOfType(s, SliccFunctionDecl.class)) {
                if (memberName.equals(f.getName())) fnCandidates.add(f);
            }
            if (!fnCandidates.isEmpty()) return pickBestFunction(fnCandidates, argTypes, callSite);
        }
        for (SliccExternalTypeDecl s : PsiTreeUtil.collectElementsOfType(file, SliccExternalTypeDecl.class)) {
            if (!structType.equals(s.getName())) continue;
            List<SliccFunctionDecl> fnCandidates = new ArrayList<>();
            for (SliccFunctionDecl f : PsiTreeUtil.collectElementsOfType(s, SliccFunctionDecl.class)) {
                if (memberName.equals(f.getName())) fnCandidates.add(f);
            }
            if (!fnCandidates.isEmpty()) return pickBestFunction(fnCandidates, argTypes, callSite);
        }
        return null;
    }

    /**
     * Extracts the value of the 'interface' attribute from a structure declaration.
     * e.g. structure(SpSequencer, interface = "Sequencer", ...) → "Sequencer"
     */
    @Nullable
    private static String getStructureInterface(SliccStructureDecl s) {
        com.intellij.lang.ASTNode attrList = s.getNode().findChildByType(SliccTypes.ATTRIBUTE_LIST);
        if (attrList == null) return null;
        for (com.intellij.lang.ASTNode attr = attrList.getFirstChildNode();
             attr != null; attr = attr.getTreeNext()) {
            if (attr.getElementType() != SliccTypes.ATTRIBUTE) continue;
            com.intellij.lang.ASTNode key = attr.getFirstChildNode();
            if (key == null || key.getElementType() != SliccTokenTypes.KW_INTERFACE) continue;
            // find the STRING_LITERAL value
            for (com.intellij.lang.ASTNode n = key.getTreeNext(); n != null; n = n.getTreeNext()) {
                if (n.getElementType() == SliccTokenTypes.STRING_LITERAL) {
                    String raw = n.getText();
                    // strip surrounding quotes
                    if (raw.length() >= 2 && raw.charAt(0) == '"')
                        return raw.substring(1, raw.length() - 1);
                    return raw;
                }
            }
        }
        return null;
    }

    @Nullable
    private static PsiElement searchLocalScope(PsiElement element, String key) {
        PsiElement cur = element.getParent();
        while (cur != null && !(cur instanceof PsiFile)) {
            if (cur instanceof SliccActionDecl
                    || cur instanceof SliccInPortDecl
                    || cur instanceof SliccFunctionDecl) {
                // Search only declarations that are visible from element's position:
                // walk the ancestor chain from element up to this scope boundary,
                // collecting FIELD_DECL siblings that appear before each ancestor node.
                PsiElement result = searchVisibleLocals(element, cur, key);
                if (result != null) return result;

                // If this is a struct member function, also search the struct's direct fields
                if (cur instanceof SliccFunctionDecl && cur.getParent() instanceof SliccStructureDecl struct) {
                    for (SliccFieldDecl field : PsiTreeUtil.collectElementsOfType(struct, SliccFieldDecl.class)) {
                        if (field.getParent() != struct) continue;
                        if (key.equals(field.getName())) {
                            PsiElement nameId = field.getNameIdentifier();
                            PsiElement target = nameId != null ? nameId : field;
                            if (target != element) return target;
                        }
                    }
                }
                return null; // reached the body scope — stop, don't escalate
            }
            cur = cur.getParent();
        }
        return null;
    }

    /**
     * Searches for a local variable named {@code key} that is visible from {@code element}
     * within the scope bounded by {@code scopeRoot} (action/function/in_port body).
     *
     * Only considers FIELD_DECL nodes that are in the same block as an ancestor of
     * {@code element} and appear before that ancestor — i.e., respects block scoping.
     */
    @Nullable
    private static PsiElement searchVisibleLocals(PsiElement element, PsiElement scopeRoot, String key) {
        // Walk from element up to scopeRoot, at each BLOCK level collect preceding siblings
        PsiElement child = element;
        PsiElement parent = child.getParent();
        while (parent != null && parent != scopeRoot) {
            if (parent instanceof SliccCompositeElement
                    && parent.getNode().getElementType() == SliccTypes.BLOCK) {
                // Scan siblings that come before 'child' in this block
                for (PsiElement sib = parent.getFirstChild(); sib != null && sib != child; sib = sib.getNextSibling()) {
                    if (sib instanceof SliccFieldDecl field && key.equals(field.getName())) {
                        PsiElement nameId = field.getNameIdentifier();
                        PsiElement target = nameId != null ? nameId : field;
                        if (target != element) return target;
                    }
                }
            }
            child = parent;
            parent = parent.getParent();
        }
        return null;
    }

    /**
     * Public API for completion: returns all variables visible at {@code position}.
     * Includes (in order): local vars declared before the cursor (block-scoped),
     * function parameters (if inside a function), and machine-level fields.
     */
    @org.jetbrains.annotations.NotNull
    public static List<SliccFieldDecl> collectVisibleVariables(@org.jetbrains.annotations.NotNull PsiElement position) {
        return collectVisibleVariables(position, position.getContainingFile());
    }

    @org.jetbrains.annotations.NotNull
    public static List<SliccFieldDecl> collectVisibleVariables(@org.jetbrains.annotations.NotNull PsiElement position,
                                                                @org.jetbrains.annotations.Nullable PsiFile originalFile) {
        List<SliccFieldDecl> result = new ArrayList<>();
        java.util.Set<String> seenNames = new java.util.HashSet<>();

        PsiFile fileForLookup = (originalFile != null && originalFile.getVirtualFile() != null)
                ? originalFile : position.getContainingFile();

        // 1. Local vars visible from position — walk up, collect preceding FIELD_DECL siblings in each BLOCK
        PsiElement child = position;
        PsiElement parent = child.getParent();
        PsiElement scopeRoot = null;
        while (parent != null && !(parent instanceof PsiFile)) {
            if (parent instanceof SliccActionDecl
                    || parent instanceof SliccFunctionDecl
                    || parent instanceof SliccInPortDecl) {
                scopeRoot = parent;
                break;
            }
            if (parent instanceof SliccCompositeElement
                    && parent.getNode().getElementType() == SliccTypes.BLOCK) {
                for (PsiElement sib = parent.getFirstChild(); sib != null && sib != child; sib = sib.getNextSibling()) {
                    if (sib instanceof SliccFieldDecl field) {
                        String n = field.getName();
                        if (n != null && seenNames.add(n)) result.add(field);
                    }
                }
            }
            child = parent;
            parent = parent.getParent();
        }

        // 2. Function parameters (if inside a function or in_port)
        if (scopeRoot instanceof SliccFunctionDecl fn) {
            com.intellij.lang.ASTNode paramList = fn.getNode().findChildByType(SliccTypes.PARAM_LIST);
            if (paramList != null) {
                for (com.intellij.lang.ASTNode n = paramList.getFirstChildNode(); n != null; n = n.getTreeNext()) {
                    if (n.getElementType() == SliccTypes.FIELD_DECL && n.getPsi() instanceof SliccFieldDecl field) {
                        String name = field.getName();
                        if (name != null && seenNames.add(name)) result.add(field);
                    }
                }
            }
        }

        // 3. Machine-level fields — walk up to the enclosing MACHINE_DECL.
        // If the action is in an included file, the PSI ancestor walk won't find a
        // SliccMachineDecl, so fall back to searching machine-scope files.
        PsiElement cur = scopeRoot != null ? scopeRoot.getParent() : position.getParent();
        SliccMachineDecl foundMachine = null;
        while (cur != null && !(cur instanceof PsiFile)) {
            if (cur instanceof SliccMachineDecl machine) {
                foundMachine = machine;
                break;
            }
            cur = cur.getParent();
        }
        if (foundMachine != null) {            collectMachineLevelFields(foundMachine, result, seenNames);
        } else {
            // Cross-file fallback: action is in an included .sm file
            PsiFile file = fileForLookup;
            if (file != null) {
                try {
                    Set<PsiFile> machineFiles = SliccProtocolIndex.machineFilesContaining(file);
                    for (PsiFile mf : machineFiles) {
                        for (SliccMachineDecl md : PsiTreeUtil.collectElementsOfType(mf, SliccMachineDecl.class)) {
                            collectMachineLevelFields(md, result, seenNames);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        return result;
    }

    private static void collectMachineLevelFields(SliccMachineDecl machine,
                                                   List<SliccFieldDecl> result,
                                                   java.util.Set<String> seenNames) {
        for (PsiElement ch = machine.getFirstChild(); ch != null; ch = ch.getNextSibling()) {
            if (ch instanceof SliccFieldDecl field) {
                String n = field.getName();
                if (n != null && seenNames.add(n)) result.add(field);
            } else if (ch instanceof SliccStructureDecl struct) {
                // Include fields from 'global { }' blocks (accessible without dot)
                com.intellij.lang.ASTNode first = struct.getNode().getFirstChildNode();
                if (first != null && first.getElementType() == SliccTokenTypes.IDENTIFIER
                        && "global".equals(first.getText())) {
                    for (SliccFieldDecl gf : PsiTreeUtil.collectElementsOfType(struct, SliccFieldDecl.class)) {
                        String n = gf.getName();
                        if (n != null && seenNames.add(n)) result.add(gf);
                    }
                }
            }
        }
    }

    @Nullable
    private static PsiElement findTypeInAllScopes(String typeName, PsiFile file) {
        PsiElement result = searchInFile(file, typeName, true, false, null, "self", null, null);
        if (result != null) return result;
        try {
            for (PsiFile f : SliccProtocolIndex.machineFilesContaining(file)) {
                result = searchInFile(f, typeName, true, false, null, "machine", null, null);
                if (result != null) return result;
            }
        } catch (Exception ignored) {}
        try {
            for (PsiFile f : SliccProtocolIndex.groupContaining(file)) {
                result = searchInFile(f, typeName, true, false, null, "protocol", null, null);
                if (result != null) return result;
            }
        } catch (Exception ignored) {}
        try {
            for (PsiFile f : SliccProtocolIndex.globalFiles(file.getProject())) {
                result = searchInFile(f, typeName, true, false, null, "global", null, null);
                if (result != null) return result;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
