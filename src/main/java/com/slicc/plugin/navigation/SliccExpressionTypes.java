package com.slicc.plugin.navigation;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.lexer.SliccBuiltinFunctions;
import com.slicc.plugin.psi.SliccEnumMember;
import com.slicc.plugin.psi.SliccEnumerationDecl;
import com.slicc.plugin.psi.SliccExternalTypeDecl;
import com.slicc.plugin.psi.SliccFieldDecl;
import com.slicc.plugin.psi.SliccFunctionDecl;
import com.slicc.plugin.psi.SliccNamedElement;
import com.slicc.plugin.psi.SliccPsiUtil;
import com.slicc.plugin.psi.SliccStateDecl;
import com.slicc.plugin.psi.SliccStructureDecl;
import com.slicc.plugin.psi.SliccTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SliccExpressionTypes {
    private static final Set<String> BUILTINS = Set.of("void", "bool", "int", "std::string");
    private static final Set<String> NUMERIC = Set.of("int", "float", "double");
    private static final Value UNKNOWN = new Value(null, false);

    private SliccExpressionTypes() {}

    public enum Compatibility { MATCH, MISMATCH, UNKNOWN }

    public record Value(@Nullable String type, boolean containsCall) {}

    public record Argument(@NotNull Value value, @NotNull TextRange range) {}

    public record Call(@NotNull List<Argument> arguments, @NotNull List<SliccFunctionDecl> candidates,
                       @NotNull List<SliccFunctionDecl> viable, boolean complete) {}

    private record Members(Map<String, String> fields, Map<String, List<SliccFunctionDecl>> functions,
                           boolean complete) {}

    private record ExpressionRange(ASTNode first, ASTNode last) {}

    public static final class Session {
        private final SliccFunctionNavigation.Scope scope;
        private final boolean available;
        private final Map<String, Members> memberTables = new HashMap<>();
        private final Map<String, SliccNamedElement> declarations = new HashMap<>();
        private final Map<ASTNode, Call> calls = new HashMap<>();
        private final Set<ASTNode> inspected = new HashSet<>();
        private final Set<ASTNode> active = new HashSet<>();
        private final Map<ExpressionRange, Value> expressions = new HashMap<>();
        private int depth;

        public Session(@NotNull PsiElement position) {
            available = !DumbService.isDumb(position.getProject());
            scope = available ? new SliccFunctionNavigation.Scope(position) : null;
        }

        @NotNull
        public Value infer(@NotNull List<ASTNode> expression) {
            if (!available || scope.hasAmbiguousOwners()) return UNKNOWN;
            List<ASTNode> tokens = new ArrayList<>();
            for (ASTNode token : expression) addTokens(token, tokens);
            return infer(tokens, 0, tokens.size());
        }

        @Nullable
        public Call inspectCall(@NotNull PsiElement name) {
            if (!available || scope.hasAmbiguousOwners()) return null;
            ASTNode node = name.getNode();
            if (node == null || node.getElementType() != SliccTokenTypes.IDENTIFIER
                    && !SliccBuiltinFunctions.isToken(node.getElementType())) return null;
            ASTNode next = SliccPsiUtil.nextSignificantSibling(node);
            if (next == null || next.getElementType() != SliccTokenTypes.OP_LPAREN) return null;
            if (calls.containsKey(node)) return calls.get(node);
            ASTNode parent = node.getTreeParent();
            if (parent == null || !inspected.add(parent)) return null;
            List<ASTNode> tokens = new ArrayList<>();
            for (ASTNode child = parent.getFirstChildNode(); child != null; child = child.getTreeNext()) {
                if (child.getElementType() != SliccTypes.BLOCK) addTokens(child, tokens);
            }
            for (int index = 0; index < tokens.size(); index++) {
                ProgressManager.checkCanceled();
                if (!startsValue(tokens.get(index))) continue;
                Parser parser = new Parser(tokens, index, tokens.size());
                parser.primary();
                if (parser.index > index) index = parser.index - 1;
            }
            return calls.get(node);
        }

        @NotNull
        public Compatibility compatibility(@Nullable String actual, @Nullable String expected) {
            if (!available || scope.hasAmbiguousOwners() || actual == null || expected == null
                    || actual.isBlank() || expected.isBlank()) return Compatibility.UNKNOWN;
            actual = SliccPsiUtil.baseTypeName(actual);
            expected = SliccPsiUtil.baseTypeName(expected);
            String actualBase = SliccPsiUtil.baseTypeName(actual);
            String expectedBase = SliccPsiUtil.baseTypeName(expected);
            if (!known(actualBase) || !known(expectedBase)) return Compatibility.UNKNOWN;
            if (!completeType(actualBase) || !completeType(expectedBase)) return Compatibility.UNKNOWN;
            if (Objects.equals(actual, expected)) return Compatibility.MATCH;
            if ("void".equals(actual) || "void".equals(expected)) return Compatibility.MISMATCH;
            if (inherits(actualBase, expectedBase)) return Compatibility.MATCH;
            return Compatibility.MISMATCH;
        }

        private Value infer(List<ASTNode> tokens, int start, int end) {
            ProgressManager.checkCanceled();
            if (start >= end || depth >= 64) return UNKNOWN;
            if (!balanced(tokens, start, end)) return UNKNOWN;
            ExpressionRange range = new ExpressionRange(tokens.get(start), tokens.get(end - 1));
            Value cached = expressions.get(range);
            if (cached != null) return cached;
            depth++;
            try {
                Parser parser = new Parser(tokens, start, end);
                Value value = parser.primary();
                if (parser.index == end) {
                    expressions.put(range, value);
                    return value;
                }
                boolean containsCall = value.containsCall();
                while (parser.index < end) {
                    if (!startsValue(tokens.get(parser.index))) {
                        parser.index++;
                        continue;
                    }
                    int previous = parser.index;
                    Value remainder = parser.primary();
                    containsCall |= remainder.containsCall();
                    if (parser.index <= previous) parser.index++;
                }
                Value result = new Value(null, containsCall);
                expressions.put(range, result);
                return result;
            } finally {
                depth--;
            }
        }

        private final class Parser {
            private final List<ASTNode> tokens;
            private final int end;
            private int index;

            private Parser(List<ASTNode> tokens, int start, int end) {
                this.tokens = tokens;
                this.index = start;
                this.end = end;
            }

            private Value primary() {
                if (index >= end) return UNKNOWN;
                ProgressManager.checkCanceled();
                ASTNode first = tokens.get(index++);
                IElementType kind = first.getElementType();
                Value value;
                if (kind == SliccTokenTypes.OP_LPAREN) {
                    int closing = closing(index - 1);
                    if (closing < 0) return UNKNOWN;
                    value = infer(tokens, index, closing);
                    index = closing + 1;
                } else if (kind == SliccTokenTypes.INTEGER_LITERAL) {
                    value = new Value("int", false);
                } else if (kind == SliccTokenTypes.FLOAT_LITERAL) {
                    value = new Value("int", false);
                } else if (kind == SliccTokenTypes.STRING_LITERAL) {
                    value = new Value("std::string", false);
                } else if (kind == SliccTokenTypes.OP_MINUS || kind == SliccTokenTypes.OP_PLUS) {
                    if (depth >= 64) return UNKNOWN;
                    depth++;
                    try {
                        value = primary();
                    } finally {
                        depth--;
                    }
                    if (value.type() == null || !NUMERIC.contains(value.type())) value = new Value(null, value.containsCall());
                } else if (kind == SliccTokenTypes.KW_IS_VALID || kind == SliccTokenTypes.KW_IS_INVALID) {
                    if (!at(SliccTokenTypes.OP_LPAREN)) return UNKNOWN;
                    int closing = closing(index);
                    if (closing < 0) return UNKNOWN;
                    Value argument = infer(tokens, index + 1, closing);
                    index = closing + 1;
                    value = new Value(argument.containsCall() && argument.type() == null ? null : "bool", argument.containsCall());
                } else if (kind == SliccTokenTypes.KW_NEW) {
                    StringBuilder type = new StringBuilder();
                    while (index < end) {
                        IElementType token = tokens.get(index).getElementType();
                        if (token != SliccTokenTypes.IDENTIFIER && token != SliccTokenTypes.OP_SCOPE) break;
                        type.append(tokens.get(index++).getText());
                    }
                    Value arguments = UNKNOWN;
                    if (at(SliccTokenTypes.OP_LPAREN)) {
                        int closing = closing(index);
                        if (closing < 0) return UNKNOWN;
                        arguments = infer(tokens, index + 1, closing);
                        index = closing + 1;
                    }
                    value = new Value(known(type.toString()) && !(arguments.containsCall() && arguments.type() == null)
                            ? type.toString() : null, arguments.containsCall());
                } else if (kind == SliccTokenTypes.KW_STATIC_CAST) {
                    value = cast();
                } else if (SliccBuiltinFunctions.isToken(kind)) {
                    if (!at(SliccTokenTypes.OP_LPAREN)) return UNKNOWN;
                    value = call(first, List.of());
                } else if (kind == SliccTokenTypes.IDENTIFIER) {
                    boolean detachedMember = index >= 2 && (tokens.get(index - 2).getElementType() == SliccTokenTypes.OP_DOT
                            || tokens.get(index - 2).getElementType() == SliccTokenTypes.OP_SCOPE
                            || tokens.get(index - 2).getElementType() == SliccTokenTypes.OP_COLON);
                    String owner = null;
                    String name = first.getText();
                    ASTNode functionName = first;
                    while (at(SliccTokenTypes.OP_SCOPE) && index + 1 < end
                            && tokens.get(index + 1).getElementType() == SliccTokenTypes.IDENTIFIER) {
                        owner = owner == null ? name : owner + "::" + name;
                        index++;
                        functionName = tokens.get(index++);
                        name = functionName.getText();
                    }
                    if (at(SliccTokenTypes.OP_LPAREN)) {
                        List<SliccFunctionDecl> candidates = detachedMember ? List.of() : owner == null
                                ? freeCandidates(name, first.getPsi()) : memberCandidates(owner, name);
                        value = call(functionName, candidates);
                    } else if (owner != null) {
                        value = constant(owner, name);
                    } else if (at(SliccTokenTypes.OP_COLON) && index + 1 < end
                            && tokens.get(index + 1).getElementType() == SliccTokenTypes.IDENTIFIER) {
                        index++;
                        value = constant(name, tokens.get(index++).getText());
                    } else {
                        value = variable(first);
                    }
                } else {
                    return UNKNOWN;
                }
                while (at(SliccTokenTypes.OP_DOT) && index + 1 < end
                        && tokens.get(index + 1).getElementType() == SliccTokenTypes.IDENTIFIER) {
                    index++;
                    ASTNode member = tokens.get(index++);
                    String type = SliccPsiUtil.baseTypeName(value.type());
                    if (at(SliccTokenTypes.OP_LPAREN)) {
                        Value returned = call(member, type == null ? List.of() : memberCandidates(type, member.getText()));
                        value = new Value(returned.type(), value.containsCall() || returned.containsCall());
                    } else {
                        Members members = type == null ? null : members(type);
                        value = new Value(members != null && members.complete()
                                ? members.fields().get(member.getText()) : null, value.containsCall());
                    }
                }
                return value;
            }

            private Value cast() {
                if (!at(SliccTokenTypes.OP_LPAREN)) return UNKNOWN;
                int opening = index;
                int closing = closing(opening);
                if (closing < 0) return UNKNOWN;
                int comma = opening + 1;
                StringBuilder type = new StringBuilder();
                while (comma < closing && tokens.get(comma).getElementType() != SliccTokenTypes.OP_COMMA) {
                    ASTNode token = tokens.get(comma++);
                    IElementType kind = token.getElementType();
                    if (kind != SliccTokenTypes.IDENTIFIER && kind != SliccTokenTypes.KW_VOID
                            && kind != SliccTokenTypes.OP_SCOPE && kind != SliccTokenTypes.OP_STAR
                            && kind != SliccTokenTypes.OP_AMP) {
                        index = closing + 1;
                        return UNKNOWN;
                    }
                    type.append(token.getText());
                }
                Value source = infer(tokens, comma + 1, closing);
                index = closing + 1;
                return new Value(comma + 1 < closing && known(SliccPsiUtil.baseTypeName(type.toString()))
                        && !(source.containsCall() && source.type() == null)
                        ? type.toString() : null, source.containsCall());
            }

            private Value call(ASTNode name, List<SliccFunctionDecl> candidates) {
                int opening = index;
                int closing = closing(opening);
                if (closing < 0) {
                    index = end;
                    calls.putIfAbsent(name, new Call(List.of(), candidates, List.of(), false));
                    return new Value(null, true);
                }
                if (!active.add(name)) {
                    index = closing + 1;
                    return new Value(null, true);
                }
                try {
                    List<Argument> arguments = new ArrayList<>();
                    int start = opening + 1;
                    int nesting = 0;
                    boolean complete = true;
                    for (int cursor = start; cursor <= closing; cursor++) {
                        IElementType kind = tokens.get(cursor).getElementType();
                        if (cursor == closing || kind == SliccTokenTypes.OP_COMMA && nesting == 0) {
                            if (cursor > start) {
                                arguments.add(new Argument(infer(tokens, start, cursor), new TextRange(
                                        tokens.get(start).getStartOffset(), tokens.get(cursor - 1).getTextRange().getEndOffset())));
                            } else if (cursor != closing || start != opening + 1) {
                                complete = false;
                            }
                            start = cursor + 1;
                        } else if (kind == SliccTokenTypes.OP_LPAREN || kind == SliccTokenTypes.OP_LBRACKET
                                || kind == SliccTokenTypes.OP_LBRACE) {
                            nesting++;
                        } else if (kind == SliccTokenTypes.OP_RPAREN || kind == SliccTokenTypes.OP_RBRACKET
                                || kind == SliccTokenTypes.OP_RBRACE) {
                            nesting--;
                            if (nesting < 0) complete = false;
                        }
                    }
                    complete &= nesting == 0;
                    List<SliccFunctionDecl> viable = complete ? select(candidates, arguments) : List.of();
                    Call call = new Call(List.copyOf(arguments), List.copyOf(candidates), List.copyOf(viable), complete);
                    calls.put(name, call);
                    index = closing + 1;
                    if (complete && SliccBuiltinFunctions.isToken(name.getElementType())) return new Value("void", true);
                    if (arguments.stream().anyMatch(argument -> argument.value().type() == null)) return new Value(null, true);
                    String returnType = null;
                    for (SliccFunctionDecl function : viable) {
                        String candidate = SliccPsiUtil.typeText(function.getNode().findChildByType(SliccTypes.TYPE_REF));
                        if (candidate.isBlank() || !known(SliccPsiUtil.baseTypeName(candidate))
                                || !completeType(SliccPsiUtil.baseTypeName(candidate))) return new Value(null, true);
                        List<String> parameters = function.getParameterTypes();
                        for (int parameter = 0; parameter < parameters.size(); parameter++) {
                            if (compatibility(arguments.get(parameter).value().type(), parameters.get(parameter))
                                    == Compatibility.UNKNOWN) return new Value(null, true);
                        }
                        if (returnType != null && !Objects.equals(SliccPsiUtil.baseTypeName(returnType),
                                SliccPsiUtil.baseTypeName(candidate))) return new Value(null, true);
                        returnType = candidate;
                    }
                    return new Value(returnType, true);
                } finally {
                    active.remove(name);
                }
            }

            private boolean at(IElementType kind) {
                return index < end && tokens.get(index).getElementType() == kind;
            }

            private int closing(int opening) {
                List<IElementType> delimiters = new ArrayList<>();
                for (int cursor = opening; cursor < end; cursor++) {
                    ProgressManager.checkCanceled();
                    IElementType kind = tokens.get(cursor).getElementType();
                    if (isOpening(kind)) delimiters.add(kind);
                    else if (isClosing(kind)) {
                        if (delimiters.isEmpty() || !matches(delimiters.remove(delimiters.size() - 1), kind)) return -1;
                        if (delimiters.isEmpty()) return cursor;
                    }
                }
                return -1;
            }
        }

        private List<SliccFunctionDecl> select(List<SliccFunctionDecl> candidates, List<Argument> arguments) {
            List<SliccFunctionDecl> selected = new ArrayList<>();
            int bestScore = -1;
            for (SliccFunctionDecl function : candidates) {
                ProgressManager.checkCanceled();
                if (!wellFormedFunction(function)) continue;
                List<String> parameters = function.getParameterTypes();
                if (parameters.size() != arguments.size()) continue;
                int score = 0;
                boolean rejected = false;
                for (int index = 0; index < parameters.size(); index++) {
                    String actual = arguments.get(index).value().type();
                    String expected = parameters.get(index);
                    Compatibility match = compatibility(actual, expected);
                    if (match == Compatibility.MISMATCH) {
                        rejected = true;
                        break;
                    }
                    if (match == Compatibility.MATCH) {
                        score += actual != null && Objects.equals(SliccPsiUtil.baseTypeName(actual),
                                SliccPsiUtil.baseTypeName(expected)) ? 2 : 1;
                    }
                }
                if (rejected || score < bestScore) continue;
                if (score > bestScore) {
                    selected.clear();
                    bestScore = score;
                }
                selected.add(function);
            }
            return selected;
        }

        private Value variable(ASTNode nameNode) {
            String name = nameNode.getText();
            if ("true".equals(name) || "false".equals(name)) return new Value("bool", false);
            PsiElement position = nameNode.getPsi();
            if (SliccMessageScope.isMessageVariable(name)) return new Value(SliccMessageScope.resolveType(name, position), false);
            for (SliccFieldDecl variable : SliccGotoDeclarationHandler.collectVisibleVariables(position)) {
                if (!name.equals(variable.getName())) continue;
                if (variable.getParent() instanceof SliccStructureDecl declaration) {
                    SliccStructureDecl enclosing = PsiTreeUtil.getParentOfType(position, SliccStructureDecl.class);
                    SliccStructureDecl owner = enclosing != null ? enclosing : declaration;
                    if (owner.getName() == null || uniqueType(owner.getName()) != owner) return UNKNOWN;
                    Members members = members(owner.getName());
                    return new Value(members.complete() ? members.fields().get(name) : null, false);
                }
                return new Value(SliccPsiUtil.typeText(variable.getNode().findChildByType(SliccTypes.TYPE_REF)), false);
            }
            String implicitType = SliccGotoDeclarationHandler.resolveImplicitVariableType(name, position);
            if (implicitType != null) return new Value(implicitType, false);
            SliccEnumMember member = SliccMemberNavigation.resolveConstant(null, name, position);
            if (member != null && member.getParent() instanceof SliccNamedElement owner) return new Value(owner.getName(), false);
            return UNKNOWN;
        }

        private Value constant(String owner, String name) {
            SliccNamedElement declaration = uniqueType(owner);
            if (!(declaration instanceof SliccEnumerationDecl || declaration instanceof SliccStateDecl)) return UNKNOWN;
            for (PsiElement child : declaration.getChildren()) {
                if (child instanceof SliccEnumMember member && name.equals(member.getName())) return new Value(owner, false);
            }
            return UNKNOWN;
        }

        private List<SliccFunctionDecl> freeCandidates(String name, PsiElement position) {
            if (SliccMessageScope.isMessageVariable(name) && SliccMessageScope.isVisible(name, position)) return List.of();
            for (SliccFieldDecl variable : SliccGotoDeclarationHandler.collectVisibleVariables(position)) {
                if (name.equals(variable.getName())) return List.of();
            }
            PsiElement owner = PsiTreeUtil.getParentOfType(position, SliccStructureDecl.class, SliccExternalTypeDecl.class);
            if (owner instanceof SliccNamedElement type && type.getName() != null) {
                List<SliccFunctionDecl> methods = memberCandidates(type.getName(), name);
                if (!methods.isEmpty()) return methods;
                if (!members(type.getName()).complete()) return List.of();
            }
            int bestRank = Integer.MAX_VALUE;
            List<SliccFunctionDecl> candidates = new ArrayList<>();
            for (PsiFile file : scope.files) {
                for (SliccFunctionDecl candidate : SliccFileSymbols.named(file, name, SliccFunctionDecl.class)) {
                    ProgressManager.checkCanceled();
                    if (PsiTreeUtil.getParentOfType(candidate, SliccStructureDecl.class, SliccExternalTypeDecl.class) != null) continue;
                    int rank = scope.rank(candidate);
                    if (rank < 0 || rank > bestRank) continue;
                    if (rank < bestRank) {
                        candidates.clear();
                        bestRank = rank;
                    }
                    candidates.add(candidate);
                }
            }
            return candidates.stream().allMatch(SliccExpressionTypes::wellFormedFunction) ? candidates : List.of();
        }

        private List<SliccFunctionDecl> memberCandidates(String type, String name) {
            Members members = members(SliccPsiUtil.baseTypeName(type));
            return members.complete() ? members.functions().getOrDefault(name, List.of()) : List.of();
        }

        private Members members(String type) {
            return memberTables.computeIfAbsent(type, this::collectMembers);
        }

        private Members collectMembers(String type) {
            Map<String, String> fields = new LinkedHashMap<>();
            Map<String, List<SliccFunctionDecl>> functions = new LinkedHashMap<>();
            Set<String> visited = new HashSet<>();
            String current = type;
            while (current != null && visited.add(current)) {
                ProgressManager.checkCanceled();
                SliccNamedElement declaration = uniqueType(current);
                if (!(declaration instanceof SliccStructureDecl || declaration instanceof SliccExternalTypeDecl)
                        || !wellFormedType(declaration)) return new Members(fields, functions, false);
                Map<String, List<SliccFunctionDecl>> direct = new LinkedHashMap<>();
                for (PsiElement child : declaration.getChildren()) {
                    if (child instanceof SliccFieldDecl field) {
                        if (field.getName() == null) return new Members(fields, functions, false);
                        fields.putIfAbsent(field.getName(), SliccPsiUtil.typeText(field.getNode().findChildByType(SliccTypes.TYPE_REF)));
                    } else if (child instanceof SliccFunctionDecl function) {
                        if (!wellFormedFunction(function)) return new Members(fields, functions, false);
                        direct.computeIfAbsent(function.getName(), unused -> new ArrayList<>()).add(function);
                    }
                }
                direct.forEach(functions::putIfAbsent);
                String parent = declaration instanceof SliccStructureDecl structure
                        ? SliccGotoDeclarationHandler.getStructureInterface(structure) : null;
                if (parent == null && attribute(declaration, "interface") != null) return new Members(fields, functions, false);
                current = parent;
            }
            return new Members(fields, functions, current == null);
        }

        private boolean known(String type) {
            return BUILTINS.contains(type) || uniqueType(type) != null;
        }

        private boolean completeType(String type) {
            if (BUILTINS.contains(type)) return true;
            SliccNamedElement declaration = uniqueType(type);
            if (declaration instanceof SliccEnumerationDecl || declaration instanceof SliccStateDecl) {
                return wellFormedType(declaration);
            }
            return declaration != null && members(type).complete();
        }

        private boolean inherits(String type, String parent) {
            if (!completeType(type) || !known(parent)) return false;
            Set<String> visited = new HashSet<>();
            String current = type;
            while (current != null && visited.add(current)) {
                if (current.equals(parent)) return true;
                SliccNamedElement declaration = uniqueType(current);
                current = declaration instanceof SliccStructureDecl structure
                        ? SliccGotoDeclarationHandler.getStructureInterface(structure) : null;
            }
            return false;
        }

        private SliccNamedElement uniqueType(String name) {
            if (declarations.containsKey(name)) return declarations.get(name);
            SliccNamedElement selected = null;
            int bestRank = Integer.MAX_VALUE;
            boolean ambiguous = false;
            for (PsiFile file : scope.files) {
                for (SliccNamedElement candidate : SliccFileSymbols.named(file, name)) {
                    ProgressManager.checkCanceled();
                    if (!(candidate instanceof SliccStructureDecl || candidate instanceof SliccExternalTypeDecl
                            || candidate instanceof SliccEnumerationDecl || candidate instanceof SliccStateDecl)) continue;
                    int rank = scope.rank(candidate);
                    if (rank < 0 || rank > bestRank) continue;
                    if (rank < bestRank) {
                        selected = candidate;
                        bestRank = rank;
                        ambiguous = false;
                    } else if (selected != candidate) {
                        ambiguous = true;
                    }
                }
            }
            declarations.put(name, ambiguous ? null : selected);
            return ambiguous ? null : selected;
        }
    }

    private static boolean startsValue(ASTNode node) {
        IElementType kind = node.getElementType();
        return kind == SliccTokenTypes.IDENTIFIER || kind == SliccTokenTypes.INTEGER_LITERAL
                || kind == SliccTokenTypes.FLOAT_LITERAL || kind == SliccTokenTypes.STRING_LITERAL
                || kind == SliccTokenTypes.OP_LPAREN || kind == SliccTokenTypes.KW_STATIC_CAST
                || kind == SliccTokenTypes.KW_NEW || SliccBuiltinFunctions.isToken(kind)
                || kind == SliccTokenTypes.KW_IS_VALID || kind == SliccTokenTypes.KW_IS_INVALID
                || kind == SliccTokenTypes.OP_MINUS || kind == SliccTokenTypes.OP_PLUS;
    }

    private static boolean balanced(List<ASTNode> tokens, int start, int end) {
        List<IElementType> delimiters = new ArrayList<>();
        for (int index = start; index < end; index++) {
            ProgressManager.checkCanceled();
            IElementType kind = tokens.get(index).getElementType();
            if (isOpening(kind)) delimiters.add(kind);
            else if (isClosing(kind)
                    && (delimiters.isEmpty() || !matches(delimiters.remove(delimiters.size() - 1), kind))) return false;
        }
        return delimiters.isEmpty();
    }

    private static boolean isOpening(IElementType kind) {
        return kind == SliccTokenTypes.OP_LPAREN || kind == SliccTokenTypes.OP_LBRACKET || kind == SliccTokenTypes.OP_LBRACE;
    }

    private static boolean isClosing(IElementType kind) {
        return kind == SliccTokenTypes.OP_RPAREN || kind == SliccTokenTypes.OP_RBRACKET || kind == SliccTokenTypes.OP_RBRACE;
    }

    private static boolean matches(IElementType opening, IElementType closing) {
        return opening == SliccTokenTypes.OP_LPAREN && closing == SliccTokenTypes.OP_RPAREN
                || opening == SliccTokenTypes.OP_LBRACKET && closing == SliccTokenTypes.OP_RBRACKET
                || opening == SliccTokenTypes.OP_LBRACE && closing == SliccTokenTypes.OP_RBRACE;
    }

    private static void addTokens(ASTNode node, List<ASTNode> tokens) {
        if (SliccPsiUtil.isTrivia(node)) return;
        if (node.getFirstChildNode() == null) {
            tokens.add(node);
            return;
        }
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) addTokens(child, tokens);
    }

    private static boolean wellFormedFunction(SliccFunctionDecl function) {
        if (function.getName() == null || SliccPsiUtil.typeText(
                function.getNode().findChildByType(SliccTypes.TYPE_REF)).isBlank()) return false;
        ASTNode parameters = function.getNode().findChildByType(SliccTypes.PARAM_LIST);
        if (parameters == null || PsiTreeUtil.findChildOfType(parameters.getPsi(), PsiErrorElement.class) != null) return false;
        ASTNode closing = SliccPsiUtil.nextSignificantSibling(parameters);
        if (closing == null || closing.getElementType() != SliccTokenTypes.OP_RPAREN) return false;
        for (ASTNode child = parameters.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getPsi() instanceof SliccFieldDecl field
                    && SliccPsiUtil.typeText(field.getNode().findChildByType(SliccTypes.TYPE_REF)).isBlank()) return false;
        }
        return true;
    }

    private static boolean wellFormedType(SliccNamedElement declaration) {
        if (PsiTreeUtil.findChildOfType(declaration, PsiErrorElement.class) != null) return false;
        if (declaration instanceof SliccExternalTypeDecl) return true;
        return declaration.getNode().findChildByType(SliccTokenTypes.OP_RBRACE) != null;
    }

    private static String attribute(SliccNamedElement declaration, String name) {
        ASTNode attributes = declaration.getNode().findChildByType(SliccTypes.ATTRIBUTE_LIST);
        if (attributes == null) return null;
        for (ASTNode attribute = attributes.getFirstChildNode(); attribute != null; attribute = attribute.getTreeNext()) {
            if (attribute.getElementType() != SliccTypes.ATTRIBUTE) continue;
            ASTNode key = attribute.getFirstChildNode();
            while (key != null && SliccPsiUtil.isTrivia(key)) key = key.getTreeNext();
            if (key == null || !name.equals(key.getText())) continue;
            for (ASTNode value = key.getTreeNext(); value != null; value = value.getTreeNext()) {
                if (value.getElementType() == SliccTokenTypes.STRING_LITERAL) {
                    String text = value.getText();
                    return text.length() >= 2 ? text.substring(1, text.length() - 1) : "";
                }
            }
            return "";
        }
        return null;
    }
}
