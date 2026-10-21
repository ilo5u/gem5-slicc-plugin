package com.slicc.plugin.documentation;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.navigation.SliccExternalTypeNavigation;
import com.slicc.plugin.navigation.SliccFileSymbols;
import com.slicc.plugin.navigation.SliccFunctionNavigation;
import com.slicc.plugin.navigation.SliccGotoDeclarationHandler;
import com.slicc.plugin.navigation.SliccProtocolIndex;
import com.slicc.plugin.psi.SliccActionDecl;
import com.slicc.plugin.psi.SliccEnumMember;
import com.slicc.plugin.psi.SliccEnumerationDecl;
import com.slicc.plugin.psi.SliccExternalTypeDecl;
import com.slicc.plugin.psi.SliccFieldDecl;
import com.slicc.plugin.psi.SliccFunctionDecl;
import com.slicc.plugin.psi.SliccInPortDecl;
import com.slicc.plugin.psi.SliccMachineDecl;
import com.slicc.plugin.psi.SliccNamedElement;
import com.slicc.plugin.psi.SliccOutPortDecl;
import com.slicc.plugin.psi.SliccPsiUtil;
import com.slicc.plugin.psi.SliccStateDecl;
import com.slicc.plugin.psi.SliccStructureDecl;
import com.slicc.plugin.psi.SliccTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

public final class SliccDocumentationProvider extends AbstractDocumentationProvider {

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        ProgressManager.checkCanceled();
        SliccNamedElement declaration = declaration(originalElement);
        if (declaration == null) declaration = declaration(element);
        if (declaration == null || declaration.getName() == null) return null;
        String description = description(declaration);
        if (description == null || description.isBlank()) return null;
        String content = StringUtil.escapeXmlEntities(decode(description))
                .replace("\r\n", "\n").replace('\r', '\n').replace("\n", "<br>")
                .replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;");
        return DocumentationMarkup.DEFINITION_START + StringUtil.escapeXmlEntities(signature(declaration))
                + DocumentationMarkup.DEFINITION_END + DocumentationMarkup.CONTENT_START + content
                + DocumentationMarkup.CONTENT_END + "<div class='bottom'>"
                + StringUtil.escapeXmlEntities(location(declaration)) + "</div>";
    }

    @Override
    public @Nullable String generateHoverDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
        return generateDoc(element, originalElement);
    }

    @Override
    public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file,
                                                             @Nullable PsiElement contextElement, int targetOffset) {
        if (targetOffset < 0 || targetOffset >= file.getTextLength()) return null;
        return declaration(file.findElementAt(targetOffset));
    }

    @Override
    public @Nullable PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
        return object instanceof SliccNamedElement named ? named : null;
    }

    private static @Nullable SliccNamedElement declaration(@Nullable PsiElement element) {
        ProgressManager.checkCanceled();
        if (element == null || !element.isValid()) return null;
        if (element instanceof SliccNamedElement named) return named;
        if (element.getNode() == null) return null;
        if (element.getNode().getElementType() == SliccTypes.REFERENCE
                || element.getNode().getElementType() == SliccTypes.TYPE_REF) {
            element = PsiTreeUtil.getDeepestFirst(element);
        }
        if (element.getNode() == null || element.getNode().getElementType() != SliccTokenTypes.IDENTIFIER) return null;
        SliccNamedElement own = PsiTreeUtil.getParentOfType(element, SliccNamedElement.class);
        if (own != null && element.equals(own.getNameIdentifier())) return own;
        if (DumbService.isDumb(element.getProject())) return null;
        PsiElement resolved = SliccGotoDeclarationHandler.resolveReference(element);
        SliccNamedElement target = resolved instanceof SliccNamedElement named ? named : null;
        if (target == null) return null;
        return visibleFiles(element).contains(target.getContainingFile()) ? target : null;
    }

    private static @Nullable String description(SliccNamedElement declaration) {
        String direct = SliccExternalTypeNavigation.attribute(declaration, "desc");
        if (direct != null && !direct.isBlank()) return direct;
        if (!(declaration instanceof SliccFunctionDecl function) || DumbService.isDumb(declaration.getProject())) return null;
        String matching = null;
        for (PsiFile file : visibleFiles(declaration)) {
            for (SliccFunctionDecl candidate : SliccFileSymbols.named(file, declaration.getName(), SliccFunctionDecl.class)) {
                ProgressManager.checkCanceled();
                if (!SliccFunctionNavigation.isSameFunction(function, candidate)) continue;
                String value = SliccExternalTypeNavigation.attribute(candidate, "desc");
                if (value == null || value.isBlank()) continue;
                if (matching != null && !matching.equals(value)) return null;
                matching = value;
            }
        }
        return matching;
    }

    private static Set<PsiFile> visibleFiles(PsiElement context) {
        Set<PsiFile> files = new LinkedHashSet<>();
        PsiFile file = context.getContainingFile();
        if (file != null) {
            files.add(file);
            files.addAll(SliccProtocolIndex.machineFilesContaining(file));
            files.addAll(SliccProtocolIndex.groupContaining(file));
            files.addAll(SliccProtocolIndex.globalFiles(context.getProject()));
        }
        return files;
    }

    private static String signature(SliccNamedElement declaration) {
        String name = declaration.getName();
        if (declaration instanceof SliccFunctionDecl function) {
            String result = SliccPsiUtil.typeText(declaration.getNode().findChildByType(SliccTypes.TYPE_REF));
            return result + " " + name + "(" + String.join(", ", function.getParameterTypes()) + ")";
        }
        if (declaration instanceof SliccFieldDecl) {
            String type = SliccPsiUtil.typeText(declaration.getNode().findChildByType(SliccTypes.TYPE_REF));
            return type + " " + name;
        }
        String kind;
        if (declaration instanceof SliccStructureDecl) kind = "structure";
        else if (declaration instanceof SliccExternalTypeDecl) kind = "external_type";
        else if (declaration instanceof SliccEnumerationDecl) kind = "enumeration";
        else if (declaration instanceof SliccStateDecl) kind = "state_declaration";
        else if (declaration instanceof SliccEnumMember) kind = "constant";
        else if (declaration instanceof SliccActionDecl) kind = "action";
        else if (declaration instanceof SliccMachineDecl) kind = "machine";
        else if (declaration instanceof SliccInPortDecl) kind = "in_port";
        else if (declaration instanceof SliccOutPortDecl) kind = "out_port";
        else kind = "declaration";
        return kind + " " + name;
    }

    private static String location(SliccNamedElement declaration) {
        String location = SliccPsiUtil.declarationLocation(declaration);
        Document document = declaration.getContainingFile().getViewProvider().getDocument();
        return document == null ? location : location + ":" + (document.getLineNumber(declaration.getTextOffset()) + 1);
    }

    private static String decode(String value) {
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < value.length(); offset++) {
            char character = value.charAt(offset);
            if (character != '\\' || offset + 1 >= value.length()) {
                result.append(character);
                continue;
            }
            char escaped = value.charAt(++offset);
            switch (escaped) {
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case '\\', '\'', '"' -> result.append(escaped);
                default -> result.append('\\').append(escaped);
            }
        }
        return result.toString();
    }
}
