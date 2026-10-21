package com.slicc.plugin.navigation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.io.UncheckedIOException;

public final class SliccExternalTypeNavigation {

    public record Result(List<SliccCppTypeTarget> targets, @Nullable String error, boolean unavailable) {}
    public record Diagnostic(@Nullable String error, boolean unavailable) {}
    private record Context(@Nullable SliccRubyBuildScope.Snapshot scope, @Nullable String failure, boolean indexing) {}
    private record Lookup(List<String> names, @Nullable SliccRubyBuildScope.Snapshot scope,
                          @Nullable String error, boolean unavailable) {}
    private static final Key<Context> ANNOTATION_CONTEXT = Key.create("SLICC_EXTERNAL_TYPE_ANNOTATION_CONTEXT");
    private static final Set<String> BUILTINS = Set.of("void", "bool", "char", "int", "float", "double", "unsigned", "long",
            "short", "size_t", "int8_t", "uint8_t", "int16_t", "uint16_t", "int32_t", "uint32_t", "int64_t", "uint64_t");

    private SliccExternalTypeNavigation() {}

    public static boolean isExternal(SliccNamedElement declaration) {
        return declaration instanceof SliccExternalTypeDecl
                || declaration instanceof SliccStructureDecl && "yes".equals(attribute(declaration, "external"));
    }

    @NotNull
    public static Result resolve(SliccNamedElement declaration) {
        Lookup lookup = lookup(declaration, null);
        if (lookup.scope() == null) return new Result(List.of(), lookup.error(), lookup.unavailable());
        PsiManager manager = declaration.getManager();
        for (String qualified : lookup.names()) {
            List<SliccCppTypeTarget> targets = new ArrayList<>();
            for (SliccRubyBuildScope.Location location : lookup.scope().definitions().getOrDefault(qualified, List.of())) {
                PsiFile file = manager.findFile(location.file());
                if (file != null) targets.add(new SliccCppTypeTarget(file, location.definition()));
            }
            if (!targets.isEmpty()) return new Result(targets, null, false);
        }
        return new Result(List.of(), missingMessage(declaration, lookup.names()), false);
    }

    @NotNull
    public static Diagnostic diagnostic(SliccNamedElement declaration, @Nullable AnnotationSession session) {
        Lookup lookup = lookup(declaration, session);
        if (lookup.scope() == null) return new Diagnostic(lookup.error(), lookup.unavailable());
        for (String qualified : lookup.names()) {
            if (!lookup.scope().definitions().getOrDefault(qualified, List.of()).isEmpty()) return new Diagnostic(null, false);
        }
        return new Diagnostic(missingMessage(declaration, lookup.names()), false);
    }

    private static Lookup lookup(SliccNamedElement declaration, @Nullable AnnotationSession session) {
        if (!isExternal(declaration) || declaration.getName() == null) return new Lookup(List.of(), null, null, false);
        String name = attribute(declaration, "external_name");
        if (name == null || name.isBlank()) name = declaration.getName();
        name = name.replaceAll("\\s+", "");
        String unqualified = name.startsWith("::") ? name.substring(2) : name;
        if (BUILTINS.contains(unqualified) || unqualified.startsWith("std::") || unqualified.equals("Scalar")) {
            return new Lookup(List.of(), null, null, false);
        }
        if (name.equals("InPort") || name.equals("OutPort")) name = "MessageBuffer";
        int template = name.indexOf('<');
        if (template >= 0) name = name.substring(0, template);
        Context context = session == null ? null : session.getUserData(ANNOTATION_CONTEXT);
        if (context == null) {
            context = context(declaration.getContainingFile());
            if (session != null) session.putUserData(ANNOTATION_CONTEXT, context);
        }
        if (context.indexing()) return new Lookup(List.of(), null, null, true);
        if (context.failure() != null) return new Lookup(List.of(), null,
                "Cannot inspect the C++ source tree for external type '" + declaration.getName() + "': " + context.failure(), true);
        if (context.scope() == null) return new Lookup(List.of(), null, "Cannot locate the Ruby Kconfig/SConscript source tree for external type '"
                + declaration.getName() + "'. Open the Ruby or gem5 source directory as a project.", true);
        List<String> names;
        if (name.startsWith("::")) names = List.of(name.substring(2));
        else if (name.startsWith("gem5::")) names = List.of(name);
        else names = declaration instanceof SliccExternalTypeDecl
                    ? List.of("gem5::ruby::" + name, "gem5::" + name)
                    : List.of("gem5::ruby::" + name);
        return new Lookup(names, context.scope(), null, false);
    }

    private static Context context(PsiFile file) {
        if (DumbService.isDumb(file.getProject())) return new Context(null, null, true);
        try {
            VirtualFile root = SliccRubyBuildScope.rootFor(file);
            return new Context(root == null ? null : SliccRubyBuildScope.snapshot(root, file.getProject()), null, false);
        } catch (UncheckedIOException exception) {
            return new Context(null, exception.getMessage(), false);
        }
    }

    private static String missingMessage(SliccNamedElement declaration, List<String> names) {
        return "No C++ definition found for external type '" + declaration.getName() + "' (expected "
                + String.join(" or ", names) + "). Check SConscript Source/MakeInclude entries and reachable headers.";
    }

    @Nullable
    public static String attribute(SliccNamedElement declaration, String name) {
        ASTNode attributes = declaration.getNode().findChildByType(SliccTypes.ATTRIBUTE_LIST);
        if (attributes == null) return null;
        for (ASTNode attribute = attributes.getFirstChildNode(); attribute != null; attribute = attribute.getTreeNext()) {
            if (attribute.getElementType() != SliccTypes.ATTRIBUTE) continue;
            ASTNode key = attribute.getFirstChildNode();
            while (key != null && SliccPsiUtil.isTrivia(key)) key = key.getTreeNext();
            if (key == null || !name.equals(key.getText())) continue;
            ASTNode equals = SliccPsiUtil.nextSignificantSibling(key);
            if (equals == null || equals.getElementType() != SliccTokenTypes.OP_EQUALS) continue;
            ASTNode value = SliccPsiUtil.nextSignificantSibling(equals);
            if (value == null) return null;
            String text = value.getText();
            return value.getElementType() == SliccTokenTypes.STRING_LITERAL && text.length() >= 2
                    ? text.substring(1, text.length() - 1) : text;
        }
        return null;
    }
}
