package com.slicc.plugin.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.slicc.plugin.icons.SliccIcons;
import com.slicc.plugin.lang.SliccLanguage;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.navigation.SliccGotoDeclarationHandler;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Code completion for SLICC files.
 *
 * Provides:
 *   1. Top-level keyword completions (machine, structure, enumeration, ...)
 *   2. Action / function names collected from the current file
 *   3. Common type names collected from declarations in scope
 */
public class SliccCompletionContributor extends CompletionContributor {

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
        com.intellij.lang.ASTNode prev = node.getTreePrev();
        while (prev != null && prev.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE)
            prev = prev.getTreePrev();
        if (prev == null || prev.getElementType() != SliccTokenTypes.OP_DOT) return false;

        // Walk back past the dot to find the owner identifier
        com.intellij.lang.ASTNode ownerNode = prev.getTreePrev();
        while (ownerNode != null && ownerNode.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE)
            ownerNode = ownerNode.getTreePrev();
        if (ownerNode == null || ownerNode.getElementType() != SliccTokenTypes.IDENTIFIER) return false;

        // Resolve the owner's type — pass originalFile so resolveVarType uses the real file
        PsiFile originalFile = parameters.getOriginalFile();
        String ownerType = SliccGotoDeclarationHandler.resolveOwnerTypeForCompletion(ownerNode, position, originalFile);
        if (ownerType == null) return false;

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
            if (name == null) continue;
            com.intellij.lang.ASTNode tr = field.getNode().findChildByType(SliccTypes.TYPE_REF);
            String typeName = "";
            if (tr != null) {
                com.intellij.lang.ASTNode id = tr.findChildByType(SliccTokenTypes.IDENTIFIER);
                if (id != null) typeName = id.getText();
            }
            result.addElement(LookupElementBuilder.create(name)
                    .withIcon(SliccIcons.STRUCTURE)
                    .withTypeText(typeName)
                    .withBoldness(true)
                    .withPsiElement(field));
        }
    }

    private static void addImplicitVarCompletions(@NotNull PsiElement position,
                                                   @NotNull CompletionResultSet result) {
        // tbe, cache_entry, address are only valid inside action bodies
        PsiElement cur = position.getParent();
        while (cur != null && !(cur instanceof com.intellij.psi.PsiFile)) {
            if (cur instanceof SliccActionDecl) {
                result.addElement(LookupElementBuilder.create("tbe")
                        .withIcon(SliccIcons.STRUCTURE).withTypeText("TBE").withBoldness(true));
                result.addElement(LookupElementBuilder.create("cache_entry")
                        .withIcon(SliccIcons.STRUCTURE).withTypeText("Entry").withBoldness(true));
                result.addElement(LookupElementBuilder.create("address")
                        .withIcon(SliccIcons.STRUCTURE).withTypeText("Addr").withBoldness(true));
                return;
            }
            cur = cur.getParent();
        }
    }

    // ...... Top-level / block keywords ........................................................................................................................

    private static final List<String> DECL_KEYWORDS = List.of(
            "machine", "structure", "enumeration", "external_type",
            "state_declaration", "in_port", "out_port",
            "action", "transition", "include"
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
            "DPRINTF",
            "set_cache_entry", "unset_cache_entry", "set_tbe", "unset_tbe"
    );

    private static final List<String> ATTR_KEYWORDS = List.of(
            "desc", "default", "network", "virtual_network",
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
                        boolean inAction = isInsideActionBody(pos);
                        addImplicitVarCompletions(pos, result);
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
            for (SliccActionDecl a : PsiTreeUtil.collectElementsOfType(file, SliccActionDecl.class)) {
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
        for (SliccFunctionDecl f : PsiTreeUtil.collectElementsOfType(file, SliccFunctionDecl.class)) {
            String name = f.getName();
            if (name != null) {
                result.addElement(LookupElementBuilder.create(name)
                        .withIcon(SliccIcons.FUNCTION)
                        .withTypeText("function")
                        .withPsiElement(f));
            }
        }
        // Structures / types
        for (SliccStructureDecl s : PsiTreeUtil.collectElementsOfType(file, SliccStructureDecl.class)) {
            String name = s.getName();
            if (name != null) {
                result.addElement(LookupElementBuilder.create(name)
                        .withIcon(SliccIcons.STRUCTURE)
                        .withTypeText("structure")
                        .withPsiElement(s));
            }
        }
        // Enumerations
        for (SliccEnumerationDecl e : PsiTreeUtil.collectElementsOfType(file, SliccEnumerationDecl.class)) {
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
