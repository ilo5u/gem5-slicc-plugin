package com.slicc.plugin.highlighting;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.navigation.SliccReference;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;

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

        // ── Declaration names ─────────────────────────────────────────────
        if (element instanceof SliccMachineDecl decl) {
            highlightName(decl, SliccSyntaxHighlighter.MACHINE_NAME, holder);

        } else if (element instanceof SliccStructureDecl decl) {
            highlightName(decl, SliccSyntaxHighlighter.STRUCTURE_NAME, holder);

        } else if (element instanceof SliccExternalTypeDecl decl) {
            highlightName(decl, SliccSyntaxHighlighter.STRUCTURE_NAME, holder);

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

            // Skip declaration names — already highlighted by the instanceof branches above
            if (element.getParent() instanceof SliccNamedElement named
                    && element.equals(named.getNameIdentifier())) {
                return;
            }

            if (isImplicitVar(text) && isInActionOrInPortBody(element)) {
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
                }
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
                } catch (Exception ignored) {}
                highlight(element, key, holder);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

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
        com.intellij.lang.ASTNode prev = node.getTreePrev();
        while (prev != null && prev.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE)
            prev = prev.getTreePrev();
        if (prev == null || prev.getElementType() != SliccTokenTypes.OP_COLON) return false;
        com.intellij.lang.ASTNode beforeColon = prev.getTreePrev();
        while (beforeColon != null && beforeColon.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE)
            beforeColon = beforeColon.getTreePrev();
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
}