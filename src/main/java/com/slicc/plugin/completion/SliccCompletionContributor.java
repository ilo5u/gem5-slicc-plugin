package com.slicc.plugin.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import com.slicc.plugin.icons.SliccIcons;
import com.slicc.plugin.lang.SliccLanguage;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.lexer.SliccBuiltinFunctions;
import com.slicc.plugin.navigation.SliccGotoDeclarationHandler;
import com.slicc.plugin.navigation.SliccMessageScope;
import com.slicc.plugin.navigation.SliccFileSymbols;
import com.slicc.plugin.navigation.SliccDebugFlags;
import com.slicc.plugin.navigation.SliccPrintfDiagnostics;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Code completion for SLICC files.
 *
 * Provides:
 *   1. Top-level keyword completions (machine, structure, enumeration, ...)
 *   2. Action / function names collected from the current file
 *   3. Common type names collected from declarations in scope
 */
public class SliccCompletionContributor extends CompletionContributor {

    private static boolean addDebugFlagCompletions(PsiElement position, PsiElement originalPosition,
                                                   CompletionResultSet result) {
        if (!SliccPrintfDiagnostics.isDebugFlagArgument(position)) return false;
        PsiElement context = originalPosition != null ? originalPosition : position;
        for (SliccDebugFlags.Flag flag : SliccDebugFlags.visible(context)) {
            result.addElement(LookupElementBuilder.create(flag.name())
                    .withIcon(SliccIcons.ENUM).withTypeText("Debug flag"));
        }
        return true;
    }

    // ...... Dot-triggered member completion ..........................................................................................................

    /**
     * If the cursor is after a dot and the owner type can be resolved, adds member
     * completions (fields + functions) and returns true, suppressing keyword/symbol
     * completions. Returns false if not in a dot-access context.
     */
    private static boolean addMemberCompletions(@NotNull CompletionParameters parameters,
                                                 @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        com.intellij.lang.ASTNode node = position.getNode();
        if (node == null) return false;

        // Walk back to find a dot immediately before this token
        com.intellij.lang.ASTNode prev = SliccPsiUtil.previousSignificantSibling(node);
        if (prev == null || prev.getElementType() != SliccTokenTypes.OP_DOT) return false;

        // Walk back past the dot to find the owner identifier
        com.intellij.lang.ASTNode ownerNode = SliccPsiUtil.previousSignificantSibling(prev);
        if (ownerNode == null || ownerNode.getElementType() != SliccTokenTypes.IDENTIFIER) return false;

        // Resolve the owner's type — pass originalFile so resolveVarType uses the real file
        PsiFile originalFile = parameters.getOriginalFile();
        String ownerType = SliccGotoDeclarationHandler.resolveOwnerTypeForCompletion(ownerNode, position, originalFile);
        if (ownerType == null) return SliccMessageScope.isMessageVariable(ownerNode.getText());

        // Collect and emit member completions
        PsiFile file = originalFile;
        for (SliccNamedElement member : SliccGotoDeclarationHandler.collectMembersOfType(ownerType, file)) {
            String name = member.getName();
            if (name == null) continue;
            if (member instanceof SliccFieldDecl field) {
                com.intellij.lang.ASTNode tr = field.getNode().findChildByType(SliccTypes.TYPE_REF);
                String typeName = "";
                if (tr != null) {
                    com.intellij.lang.ASTNode id = tr.findChildByType(SliccTokenTypes.IDENTIFIER);
                    if (id != null) typeName = id.getText();
                }
                result.addElement(LookupElementBuilder.create(name)
                        .withIcon(SliccIcons.STRUCTURE)
                        .withTypeText(typeName)
                        .withPsiElement(member));
            } else if (member instanceof SliccFunctionDecl fn) {
                com.intellij.lang.ASTNode tr = fn.getNode().findChildByType(SliccTypes.TYPE_REF);
                String retType = "";
                if (tr != null) {
                    com.intellij.lang.ASTNode id = tr.findChildByType(SliccTokenTypes.IDENTIFIER);
                    if (id != null) retType = id.getText();
                }
                result.addElement(LookupElementBuilder.create(name)
                        .withIcon(SliccIcons.FUNCTION)
                        .withTypeText(retType)
                        .withTailText("()", true)
                        .withPsiElement(member));
            }
        }
        return true;
    }

    private static void addScopeVariableCompletions(@NotNull PsiElement position,
                                                      @NotNull PsiFile originalFile,
                                                      @NotNull CompletionResultSet result) {
        for (SliccFieldDecl field : SliccGotoDeclarationHandler.collectVisibleVariables(position, originalFile)) {
            String name = field.getName();
            if (name == null || SliccMessageScope.isMessageVariable(name)
                    && SliccMessageScope.findParameter(name, position) == null) continue;
            com.intellij.lang.ASTNode tr = field.getNode().findChildByType(SliccTypes.TYPE_REF);
            String typeName = "";
            if (tr != null) typeName = SliccPsiUtil.typeText(tr);
            result.addElement(LookupElementBuilder.create(name)
                    .withIcon(SliccIcons.STRUCTURE)
                    .withTypeText(typeName)
                    .withBoldness(true)
                    .withPsiElement(field));
        }
    }

    private static void addImplicitVarCompletions(@NotNull PsiElement position,
                                                   @NotNull PsiFile originalFile,
                                                   @NotNull CompletionResultSet result) {
        for (String name : List.of("in_msg", "out_msg")) {
            if (SliccMessageScope.isVisible(name, position)
                    && SliccMessageScope.findParameter(name, position) == null) {
                result.addElement(LookupElementBuilder.create(name)
                        .withIcon(SliccIcons.STRUCTURE)
                        .withTypeText(SliccMessageScope.resolveType(name, position))
                        .withBoldness(true));
            }
        }
        Set<String> explicitNames = new HashSet<>();
        for (SliccFieldDecl field : SliccGotoDeclarationHandler.collectVisibleVariables(position, originalFile)) {
            explicitNames.add(field.getName());
        }
        for (String name : List.of("tbe", "cache_entry", "address")) {
            if (explicitNames.contains(name)) continue;
            String type = SliccGotoDeclarationHandler.resolveImplicitVariableType(name, position, originalFile);
            if (type != null) {
                result.addElement(LookupElementBuilder.create(name)
                        .withIcon(SliccIcons.STRUCTURE).withTypeText(type).withBoldness(true));
            }
        }
    }

    // ...... Top-level / block keywords ........................................................................................................................

    private static final List<String> DECL_KEYWORDS = List.of(
            "machine", "structure", "enumeration", "external_type",
            "state_declaration", "in_port", "out_port",
            "action", "transition", "include", "protocol", "global"
    );

    private static final List<String> CONTROL_KEYWORDS = List.of(
            "if", "else", "return"
    );

    private static final List<String> TYPE_KEYWORDS = List.of(
            "void", "bool", "int", "float", "string"
    );

    private static final List<String> BUILTIN_STMTS = List.of(
            "peek", "enqueue", "dequeue", "trigger", "stall_and_wait",
            "check_allocate", "error", "assert",
            "wakeup_port", "check_next_cycle", "check_stop_slots", "check_on_cache_probe",
            "defer_enqueueing", "static_cast", "is_valid", "is_invalid", "new", "OOD",
            "set_cache_entry", "unset_cache_entry", "set_tbe", "unset_tbe"
    );

    private static final List<String> ATTR_KEYWORDS = List.of(
            "desc", "main", "block_on", "format", "in_trans", "out_trans", "default", "network", "virtual_network",
            "vnet_type", "rank", "interface", "external", "primitive", "ordered"
    );

    // Common gem5 / Ruby built-in types
    private static final List<String> GEM5_TYPES = List.of(
            "Addr", "Cycles", "Tick", "DataBlock", "MachineID", "NetDest",
            "MessageBuffer", "CacheMemory", "Sequencer", "RubyRequest",
            "RubyRequestType", "MachineType", "AccessPermission",
            "TBE", "Entry", "State", "Event"
    );

    public SliccCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(SliccLanguage.INSTANCE),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        boolean memberDone = addMemberCompletions(parameters, result);
                        if (memberDone) return;
                        PsiElement pos = parameters.getPosition();
                        if (addDebugFlagCompletions(pos, parameters.getOriginalPosition(), result)) return;
                        boolean inAction = isInsideActionBody(pos);
                        addImplicitVarCompletions(pos, parameters.getOriginalFile(), result);
                        addScopeVariableCompletions(pos, parameters.getOriginalFile(), result);
                        addKeywords(result);
                        addFileSymbols(parameters.getOriginalFile(), result, inAction);
                    }
                });
    }

    private static boolean isInsideActionBody(@NotNull PsiElement element) {
        PsiElement cur = element.getParent();
        while (cur != null && !(cur instanceof PsiFile)) {
            if (cur instanceof SliccActionDecl) return true;
            cur = cur.getParent();
        }
        return false;
    }

    // ...... Keyword completions .............................................................................................................................................

    private static void addKeywords(CompletionResultSet result) {
        for (String kw : DECL_KEYWORDS) {
            result.addElement(LookupElementBuilder.create(kw)
                    .withBoldness(true)
                    .withTypeText("SLICC keyword"));
        }
        for (String kw : CONTROL_KEYWORDS) {
            result.addElement(LookupElementBuilder.create(kw)
                    .withBoldness(true)
                    .withTypeText("keyword"));
        }
        for (String kw : TYPE_KEYWORDS) {
            result.addElement(LookupElementBuilder.create(kw)
                    .withBoldness(true)
                    .withTypeText("type"));
        }
        for (String kw : BUILTIN_STMTS) {
            result.addElement(LookupElementBuilder.create(kw)
                    .withIcon(SliccIcons.FUNCTION)
                    .withTypeText("built-in"));
        }
        for (String name : SliccBuiltinFunctions.names()) {
            result.addElement(LookupElementBuilder.create(name)
                    .withIcon(SliccIcons.FUNCTION)
                    .withTypeText("built-in"));
        }
        for (String kw : ATTR_KEYWORDS) {
            result.addElement(LookupElementBuilder.create(kw)
                    .withTypeText("attribute"));
        }
        for (String t : GEM5_TYPES) {
            result.addElement(LookupElementBuilder.create(t)
                    .withIcon(SliccIcons.STRUCTURE)
                    .withTypeText("gem5 type"));
        }
    }

    // ...... File-local symbol completions ...............................................................................................................

    private static void addFileSymbols(PsiFile file, CompletionResultSet result, boolean skipActions) {
        // Actions
        if (!skipActions) {
            for (SliccActionDecl a : SliccFileSymbols.ofType(file, SliccActionDecl.class)) {
                String name = a.getName();
                if (name != null) {
                    result.addElement(LookupElementBuilder.create(name)
                            .withIcon(SliccIcons.ACTION)
                            .withTypeText("action")
                            .withPsiElement(a));
                }
            }
        }
        // Functions
        for (SliccFunctionDecl f : SliccGotoDeclarationHandler.collectVisibleFunctions(file)) {
            String name = f.getName();
            if (name != null) {
                com.intellij.lang.ASTNode returnType = f.getNode().findChildByType(SliccTypes.TYPE_REF);
                StringJoiner signature = new StringJoiner(", ", "(", ")");
                com.intellij.lang.ASTNode parameters = f.getNode().findChildByType(SliccTypes.PARAM_LIST);
                if (parameters != null) {
                    for (com.intellij.lang.ASTNode parameter = parameters.getFirstChildNode();
                         parameter != null; parameter = parameter.getTreeNext()) {
                        if (!(parameter.getPsi() instanceof SliccFieldDecl field)) continue;
                        com.intellij.lang.ASTNode type = parameter.findChildByType(SliccTypes.TYPE_REF);
                        String parameterName = field.getName();
                        signature.add((type != null ? type.getText() : "")
                                + (parameterName != null ? " " + parameterName : ""));
                    }
                }
                result.addElement(LookupElementBuilder.create(f, name)
                        .withIcon(SliccIcons.FUNCTION)
                        .withTypeText(returnType != null ? returnType.getText() : "function")
                        .withTailText(signature.toString(), true)
                        .withPsiElement(f));
            }
        }
        // Structures / types
        for (SliccStructureDecl s : SliccFileSymbols.ofType(file, SliccStructureDecl.class)) {
            String name = s.getName();
            if (name != null) {
                result.addElement(LookupElementBuilder.create(name)
                        .withIcon(SliccIcons.STRUCTURE)
                        .withTypeText("structure")
                        .withPsiElement(s));
            }
        }
        // Enumerations
        for (SliccEnumerationDecl e : SliccFileSymbols.ofType(file, SliccEnumerationDecl.class)) {
            String name = e.getName();
            if (name != null) {
                result.addElement(LookupElementBuilder.create(name)
                        .withIcon(SliccIcons.ENUM)
                        .withTypeText("enumeration")
                        .withPsiElement(e));
            }
        }
    }
}
