package com.slicc.plugin.highlighting;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Applies semantic (post-lexer) highlighting to SLICC declarations so that:
 *   - machine names get CLASS_NAME colour
 *   - structure / external_type names get CLASS_NAME colour
 *   - enumeration / state_declaration names get CLASS_NAME colour
 *   - action names get FUNCTION_DECLARATION colour
 *   - function names get FUNCTION_DECLARATION colour
 *   - transition labels get LABEL colour
 */
public class SliccAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element instanceof SliccMachineDecl decl) {
            highlightName(decl, SliccSyntaxHighlighter.MACHINE_NAME, holder);
        } else if (element instanceof SliccStructureDecl decl) {
            highlightName(decl, SliccSyntaxHighlighter.STRUCTURE_NAME, holder);
        } else if (element instanceof SliccEnumerationDecl decl) {
            highlightName(decl, SliccSyntaxHighlighter.ENUM_NAME, holder);
        } else if (element instanceof SliccStateDecl decl) {
            highlightName(decl, SliccSyntaxHighlighter.ENUM_NAME, holder);
        } else if (element instanceof SliccExternalTypeDecl decl) {
            highlightName(decl, SliccSyntaxHighlighter.STRUCTURE_NAME, holder);
        } else if (element instanceof SliccActionDecl decl) {
            highlightName(decl, SliccSyntaxHighlighter.ACTION_NAME, holder);
        } else if (element instanceof SliccFunctionDecl decl) {
            highlightName(decl, SliccSyntaxHighlighter.FUNCTION_NAME, holder);
        }
        // Transitions have no single identifier, their whole label is styled
        // by the structure view / find-usages; we do nothing here to keep
        // the existing state/event identifiers styled as ordinary identifiers.
    }

    private static void highlightName(SliccNamedElement decl,
                                      TextAttributesKey key,
                                      AnnotationHolder holder) {
        PsiElement nameId = decl.getNameIdentifier();
        if (nameId != null) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(nameId)
                    .textAttributes(key)
                    .create();
        }
    }
}
