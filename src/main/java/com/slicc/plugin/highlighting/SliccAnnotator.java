package com.slicc.plugin.highlighting;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.navigation.SliccProtocolIndex;
import com.slicc.plugin.navigation.SliccFileSymbols;
import com.slicc.plugin.navigation.SliccMessageScope;
import com.slicc.plugin.navigation.SliccMemberDiagnostics;
import com.slicc.plugin.navigation.SliccTypeDiagnostics;
import com.slicc.plugin.navigation.SliccReference;
import com.slicc.plugin.navigation.SliccExternalTypeNavigation;
import com.slicc.plugin.navigation.SliccPrintfDiagnostics;
import com.slicc.plugin.navigation.SliccTransitionDiagnostics;
import com.slicc.plugin.navigation.SliccActionNavigation;
import com.slicc.plugin.navigation.SliccCallDiagnostics;
import com.slicc.plugin.navigation.SliccEnumDefaults;
import com.slicc.plugin.navigation.SliccGeneratedFunctions;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Semantic (post-lexer) highlighting for SLICC declarations and type usages.
 *
 * Highlighting scheme:
 *   machine(SpPCache...)            → MACHINE_NAME    (class name colour)
 *   structure(Entry...) name        → STRUCTURE_NAME  (class name colour)
 *   external_type(CacheMemory...)   → STRUCTURE_NAME  (class name colour)
 *   enumeration(Event...) name      → ENUM_TYPE_NAME  (interface colour)
 *   state_declaration(State...) name→ ENUM_TYPE_NAME  (interface colour)
 *   enum/state members: Load, I…   → ENUM_MEMBER_VALUE (constant colour)
 *   struct field var names: state, address… → FIELD_NAME (field colour)
 *   action(Probe_Cache...)          → ACTION_NAME     (function colour)
 *   void/bool funcName(...)         → FUNCTION_NAME   (function colour)
 *
 *   Type usages resolved to structure/external_type → STRUCTURE_NAME
 *   Type usages resolved to enumeration/state_decl  → ENUM_TYPE_NAME
 */
public class SliccAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {

        if (element instanceof SliccTransitionDecl transition) {
            for (SliccTransitionDiagnostics.Diagnostic diagnostic : SliccTransitionDiagnostics.check(
                    transition, holder.getCurrentAnnotationSession())) {
                holder.newAnnotation(HighlightSeverity.ERROR, diagnostic.message()).range(diagnostic.range()).create();
            }
        }

        if (element instanceof SliccFieldDecl || element.getNode().getElementType() == SliccTypes.STATEMENT) {
            for (SliccPrintfDiagnostics.Diagnostic diagnostic : SliccPrintfDiagnostics.checkContainer(element)) {
                holder.newAnnotation(diagnostic.severity(), diagnostic.message()).range(diagnostic.range()).create();
            }
            for (SliccTypeDiagnostics.Diagnostic diagnostic : SliccTypeDiagnostics.check(
                    element, holder.getCurrentAnnotationSession())) {
                holder.newAnnotation(HighlightSeverity.ERROR, diagnostic.message()).range(diagnostic.range()).create();
            }
        }

        // ── Declaration names ─────────────────────────────────────────────
        if (element instanceof SliccMachineDecl decl) {
            highlightName(decl, SliccSyntaxHighlighter.MACHINE_NAME, holder);

        } else if (element instanceof SliccStructureDecl decl) {
            highlightName(decl, SliccSyntaxHighlighter.STRUCTURE_NAME, holder);
            checkExternalType(decl, holder);

        } else if (element instanceof SliccExternalTypeDecl decl) {
            highlightName(decl, SliccSyntaxHighlighter.STRUCTURE_NAME, holder);
            checkExternalType(decl, holder);

        } else if (element instanceof SliccEnumerationDecl decl) {
            highlightName(decl, SliccSyntaxHighlighter.ENUM_TYPE_NAME, holder);

        } else if (element instanceof SliccStateDecl decl) {
            highlightName(decl, SliccSyntaxHighlighter.ENUM_TYPE_NAME, holder);

        } else if (element instanceof SliccEnumMember decl) {
            // Load, Store, I, LM, … inside enumeration/state_declaration bodies
            highlightName(decl, SliccSyntaxHighlighter.ENUM_MEMBER_VALUE, holder);

        } else if (element instanceof SliccFieldDecl decl) {
            // Structure member variable names: state, address, DataBlk, v, d, e, pos…
            // Only highlight if the field is inside a structure (not a machine-level var)
            if (isInsideStructure(decl)) {
                highlightName(decl, SliccSyntaxHighlighter.FIELD_NAME, holder);
            }

        } else if (element instanceof SliccActionDecl decl) {
            highlightName(decl, SliccSyntaxHighlighter.ACTION_NAME, holder);

        } else if (element instanceof SliccFunctionDecl decl) {
            highlightName(decl, SliccSyntaxHighlighter.FUNCTION_NAME, holder);

        // ── Implicit built-in variables + usage-site semantic highlighting ──
        } else if (element.getNode().getElementType() == SliccTokenTypes.IDENTIFIER) {
            // Skip identifiers inside TYPE_REF — handled by the TYPE_REF branch below
            if (element.getParent() instanceof SliccCompositeElement p
                    && p.getNode().getElementType() == SliccTypes.TYPE_REF) {
                return;
            }

            String text = element.getText();

            if (SliccPrintfDiagnostics.isDebugFlagArgument(element)) {
                highlight(element, SliccSyntaxHighlighter.ENUM_MEMBER_VALUE, holder);
                return;
            }

            // Skip declaration names — already highlighted by the instanceof branches above
            if (element.getParent() instanceof SliccNamedElement named
                    && element.equals(named.getNameIdentifier())) {
                return;
            }

            if (SliccActionNavigation.isTransitionActionReference(element)) {
                highlight(element, SliccSyntaxHighlighter.FUNCTION_CALL, holder);
                return;
            }

            SliccEnumDefaults.Resolution enumDefault = SliccEnumDefaults.resolve(element);
            if (enumDefault.handled()) {
                if (enumDefault.member() != null) {
                    highlight(element, SliccSyntaxHighlighter.ENUM_MEMBER_VALUE, holder);
                } else if (!enumDefault.deferred()) {
                    holder.newAnnotation(HighlightSeverity.ERROR, "Unresolved symbol '" + text + "'")
                            .range(element).create();
                }
                return;
            }

            String missingCall = SliccCallDiagnostics.error(element);
            if (missingCall != null) {
                holder.newAnnotation(HighlightSeverity.ERROR, missingCall).range(element).create();
                return;
            }

            if (SliccGeneratedFunctions.resolve(element) != null) {
                highlight(element, SliccSyntaxHighlighter.FUNCTION_CALL, holder);
                return;
            }

            if (SliccMessageScope.isMessageVariable(text) && !isDotAccessRhs(element)
                    && !isColonAccess(element) && !isScopeAccessPart(element)
                    && SliccMessageScope.findParameter(text, element) == null) {
                if (SliccMessageScope.isVisible(text, element)) {
                    highlight(element, SliccSyntaxHighlighter.IMPLICIT_VAR, holder);
                } else {
                    String statement = "in_msg".equals(text) ? "peek" : "enqueue";
                    holder.newAnnotation(HighlightSeverity.ERROR,
                                    "'" + text + "' is only available inside a " + statement + " block")
                            .range(element)
                            .create();
                }
                return;
            }

            if (isImplicitVar(text) && isInActionOrInPortBody(element) && !isDotAccessRhs(element)) {
                highlight(element, SliccSyntaxHighlighter.IMPLICIT_VAR, holder);
                return;
            }

            // Colon-access (Event:Allocate) — always an enum/state member
            if (isColonAccess(element)) {
                highlight(element, SliccSyntaxHighlighter.ENUM_MEMBER_VALUE, holder);
                return;
            }

            // Resolve the reference and colour the usage site to match the declaration kind
            // element.getReference() does not return contributed references on leaf tokens;
            // construct SliccReference directly to guarantee resolution.
            try {
                PsiElement target = new SliccReference(element).resolve();
                if (target != null) {
                    // resolve() returns the declaration element directly
                    PsiElement decl = (target instanceof SliccNamedElement) ? target : target.getParent();
                    if (decl instanceof SliccFunctionDecl) {
                        highlight(element, SliccSyntaxHighlighter.FUNCTION_CALL, holder);
                    } else if (decl instanceof SliccActionDecl) {
                        highlight(element, SliccSyntaxHighlighter.FUNCTION_CALL, holder);
                    } else if (decl instanceof SliccEnumMember) {
                        highlight(element, SliccSyntaxHighlighter.ENUM_MEMBER_VALUE, holder);
                    } else if (decl instanceof SliccFieldDecl) {
                        highlight(element, SliccSyntaxHighlighter.FIELD_NAME, holder);
                    } else if (decl instanceof SliccStructureDecl
                            || decl instanceof SliccExternalTypeDecl) {
                        highlight(element, SliccSyntaxHighlighter.STRUCTURE_NAME, holder);
                    } else if (decl instanceof SliccEnumerationDecl
                            || decl instanceof SliccStateDecl) {
                        highlight(element, SliccSyntaxHighlighter.ENUM_TYPE_NAME, holder);
                    } else if (decl instanceof SliccMachineDecl) {
                        highlight(element, SliccSyntaxHighlighter.MACHINE_NAME, holder);
                    }
                } else if (isDotAccessRhs(element)) {
                    String error = SliccMemberDiagnostics.error(element, holder.getCurrentAnnotationSession());
                    if (error != null) {
                        holder.newAnnotation(HighlightSeverity.ERROR, error).range(element).create();
                    }
                } else if (!isBuiltinIdentifier(text)
                        && !isCallName(element)
                        && !isScopeAccessPart(element)
                        && !isAutoGeneratedMachineSymbol(text, element.getContainingFile())) {
                    holder.newAnnotation(HighlightSeverity.ERROR, "Unresolved symbol '" + text + "'")
                            .range(element)
                            .create();
                }
            } catch (ProcessCanceledException exception) {
                throw exception;
            } catch (Exception ignored) {}

            // ── Type reference usages: colour identifier based on what it resolves to ─
        } else if (element instanceof SliccCompositeElement
                && element.getNode().getElementType() == SliccTypes.TYPE_REF) {
            com.intellij.lang.ASTNode idNode = element.getNode().findChildByType(SliccTokenTypes.IDENTIFIER);
            if (idNode != null) {
                TextAttributesKey key = SliccSyntaxHighlighter.STRUCTURE_NAME; // default for unresolved types
                try {
                    PsiElement target = new SliccReference(idNode.getPsi()).resolve();
                    if (target != null) {
                        PsiElement decl = (target instanceof SliccNamedElement) ? target : target.getParent();
                        if (decl instanceof SliccEnumerationDecl || decl instanceof SliccStateDecl) {
                            key = SliccSyntaxHighlighter.ENUM_TYPE_NAME;
                        } else if (decl instanceof SliccMachineDecl) {
                            key = SliccSyntaxHighlighter.MACHINE_NAME;
                        }
                        // SliccStructureDecl, SliccExternalTypeDecl, or unresolved → STRUCTURE_NAME
                    }
                } catch (ProcessCanceledException exception) {
                    throw exception;
                } catch (Exception ignored) {}
                highlight(element, key, holder);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static void checkExternalType(SliccNamedElement declaration, AnnotationHolder holder) {
        if (!SliccExternalTypeNavigation.isExternal(declaration) || declaration.getNameIdentifier() == null) return;
        SliccExternalTypeNavigation.Diagnostic result = SliccExternalTypeNavigation.diagnostic(
                declaration, holder.getCurrentAnnotationSession());
        if (result.error() != null) {
            holder.newAnnotation(result.unavailable() ? HighlightSeverity.WARNING : HighlightSeverity.ERROR, result.error())
                    .range(declaration.getNameIdentifier()).create();
        }
    }

    private static boolean isImplicitVar(String name) {
        return name.equals("tbe") || name.equals("cache_entry") || name.equals("address");
    }

    private static boolean isInActionOrInPortBody(PsiElement element) {
        PsiElement cur = element.getParent();
        while (cur != null && !(cur instanceof PsiFile)) {
            if (cur instanceof SliccActionDecl) return true;
            cur = cur.getParent();
        }
        return false;
    }

    private static boolean isColonAccess(@NotNull PsiElement element) {
        com.intellij.lang.ASTNode node = element.getNode();
        com.intellij.lang.ASTNode prev = SliccPsiUtil.previousSignificantSibling(node);
        if (prev == null || prev.getElementType() != SliccTokenTypes.OP_COLON) return false;
        com.intellij.lang.ASTNode beforeColon = SliccPsiUtil.previousSignificantSibling(prev);
        return beforeColon != null && beforeColon.getElementType() == SliccTokenTypes.IDENTIFIER;
    }

    private static boolean isInsideStructure(@NotNull SliccFieldDecl decl) {
        PsiElement parent = decl.getParent();
        // SliccFieldDecl can appear inside STRUCTURE_DECL body or at machine params level
        // We want to highlight only those inside structure bodies
        return parent instanceof SliccStructureDecl
                || (parent != null && parent.getParent() instanceof SliccStructureDecl);
    }

    private static void highlightName(@NotNull SliccNamedElement decl,
                                      @NotNull TextAttributesKey key,
                                      @NotNull AnnotationHolder holder) {
        PsiElement nameId = decl.getNameIdentifier();
        if (nameId != null) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(nameId)
                    .textAttributes(key)
                    .create();
        }
    }

    private static void highlight(@NotNull PsiElement element,
                                  @NotNull TextAttributesKey key,
                                  @NotNull AnnotationHolder holder) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element)
                .textAttributes(key)
                .create();
    }

    private static boolean isDotAccessRhs(@NotNull PsiElement element) {
        com.intellij.lang.ASTNode node = element.getNode();
        com.intellij.lang.ASTNode prev = SliccPsiUtil.previousSignificantSibling(node);
        return prev != null && prev.getElementType() == SliccTokenTypes.OP_DOT;
    }

    private static boolean isBuiltinIdentifier(@NotNull String text) {
        return switch (text) {
            case "true", "false", "null", "NULL", "error", "assert" -> true;
            default -> false;
        };
    }

    private static boolean isCallName(PsiElement element) {
        com.intellij.lang.ASTNode next = SliccPsiUtil.nextSignificantSibling(element.getNode());
        return next != null && next.getElementType() == SliccTokenTypes.OP_LPAREN;
    }

    /** True if this identifier is part of a C++ scoped name (std::string, prefetch::Base). */
    private static boolean isScopeAccessPart(@NotNull PsiElement element) {
        com.intellij.lang.ASTNode node = element.getNode();
        com.intellij.lang.ASTNode prev = SliccPsiUtil.previousSignificantSibling(node);
        if (prev != null && prev.getElementType() == SliccTokenTypes.OP_SCOPE) return true;
        com.intellij.lang.ASTNode next = SliccPsiUtil.nextSignificantSibling(node);
        return next != null && next.getElementType() == SliccTokenTypes.OP_SCOPE;
    }

    /**
     * Returns true if the identifier looks like an auto-generated machine symbol,
     * e.g. SpPCache_State_to_permission where SpPCache is a machine declared in scope.
     * SLICC auto-generates functions/constants with the prefix {MachineName}_.
     * Searches the current file and all files in the protocol group.
     */
    private static boolean isAutoGeneratedMachineSymbol(@NotNull String text, @Nullable PsiFile file) {
        if (file == null || !text.contains("_")) return false;
        if (hasMachinePrefix(text, file)) return true;
        try {
            for (PsiFile f : SliccProtocolIndex.groupContaining(file)) {
                if (f != file && hasMachinePrefix(text, f)) return true;
            }
        } catch (ProcessCanceledException exception) {
            throw exception;
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean hasMachinePrefix(@NotNull String text, @NotNull PsiFile file) {
        for (SliccMachineDecl machine : SliccFileSymbols.ofType(file, SliccMachineDecl.class)) {
            String name = machine.getName();
            if (name != null && text.startsWith(name + "_")) return true;
        }
        return false;
    }
}
