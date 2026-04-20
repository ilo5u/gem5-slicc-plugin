package com.slicc.plugin.highlighting;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
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

            // ── Type reference usages: colour identifier based on what it resolves to ─
        } else if (element instanceof SliccCompositeElement
                && element.getNode().getElementType() == SliccTypes.TYPE_REF) {
            PsiReference ref = element.getFirstChild() != null
                    ? element.getFirstChild().getReference() : null;
            if (ref == null) ref = element.getReference();
            if (ref != null) {
                PsiElement target = ref.resolve();
                if (target instanceof SliccStructureDecl
                        || target instanceof SliccExternalTypeDecl) {
                    highlight(element, SliccSyntaxHighlighter.STRUCTURE_NAME, holder);
                } else if (target instanceof SliccEnumerationDecl
                        || target instanceof SliccStateDecl) {
                    highlight(element, SliccSyntaxHighlighter.ENUM_TYPE_NAME, holder);
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

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