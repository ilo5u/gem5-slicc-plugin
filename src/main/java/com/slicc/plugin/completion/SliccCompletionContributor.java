package com.slicc.plugin.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.slicc.plugin.icons.SliccIcons;
import com.slicc.plugin.lang.SliccLanguage;
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

    // ...... Top-level / block keywords ........................................................................................................................

    private static final List<String> DECL_KEYWORDS = List.of(
            "machine", "structure", "enumeration", "external_type",
            "state_declaration", "in_port", "out_port",
            "action", "transition", "include", "global"
    );

    private static final List<String> CONTROL_KEYWORDS = List.of(
            "if", "else", "for", "while", "return", "new"
    );

    private static final List<String> TYPE_KEYWORDS = List.of(
            "void", "bool", "int", "float", "string", "true", "false"
    );

    private static final List<String> BUILTIN_STMTS = List.of(
            "peek", "enqueue", "trigger", "stall_and_wait", "wakeup_port",
            "check_allocate", "check_stop_slots", "error", "assert",
            "DPRINTF", "DEBUG_EXPR",
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
        // Match any position in a SLICC file
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(SliccLanguage.INSTANCE),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        addKeywords(result);
                        addFileSymbols(parameters.getOriginalFile(), result);
                    }
                });
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

    private static void addFileSymbols(PsiFile file, CompletionResultSet result) {
        // Actions
        for (SliccActionDecl a : PsiTreeUtil.collectElementsOfType(file, SliccActionDecl.class)) {
            String name = a.getName();
            if (name != null) {
                result.addElement(LookupElementBuilder.create(name)
                        .withIcon(SliccIcons.ACTION)
                        .withTypeText("action")
                        .withPsiElement(a));
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
