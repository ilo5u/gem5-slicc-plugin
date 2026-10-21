package com.slicc.plugin.inlay;

import com.intellij.codeInsight.hints.declarative.HintFormat;
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector;
import com.intellij.codeInsight.hints.declarative.InlayTreeSink;
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition;
import com.intellij.codeInsight.hints.declarative.OwnBypassCollector;
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.psi.SliccFile;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

final class SliccInlayPresentation {

    private SliccInlayPresentation() {}

    static @Nullable InlayHintsCollector collector(
            PsiFile file, Function<PsiElement, List<SliccInlayHints.Hint>> hints) {
        if (!(file instanceof SliccFile) || DumbService.isDumb(file.getProject())) return null;
        return (SharedBypassCollector) (element, sink) -> {
            if (isIdentifier(element) && !DumbService.isDumb(element.getProject())) {
                render(sink, hints.apply(element));
            }
        };
    }

    static @Nullable InlayHintsCollector typeCollector(PsiFile file) {
        if (!(file instanceof SliccFile) || DumbService.isDumb(file.getProject())) return null;
        return (OwnBypassCollector) (source, sink) -> {
            if (DumbService.isDumb(source.getProject())) return;
            final SliccInlayHints.TypeCollector hints = new SliccInlayHints.TypeCollector();
            source.accept(new PsiRecursiveElementWalkingVisitor() {
                @Override
                public void visitElement(@NotNull PsiElement element) {
                    if (isIdentifier(element)) {
                        ProgressManager.checkCanceled();
                        render(sink, hints.collect(element));
                    }
                    super.visitElement(element);
                }
            });
        };
    }

    private static boolean isIdentifier(PsiElement element) {
        return element.getFirstChild() == null && element.getNode() != null
                && element.getNode().getElementType() == SliccTokenTypes.IDENTIFIER;
    }

    private static void render(InlayTreeSink sink, List<SliccInlayHints.Hint> hints) {
        for (SliccInlayHints.Hint hint : hints) {
            sink.addPresentation(new InlineInlayPosition(hint.offset(), hint.relatesToPrecedingText(), 0),
                    null, null, HintFormat.Companion.getDefault(), builder -> {
                        builder.text(hint.text(), null);
                        return Unit.INSTANCE;
                    });
        }
    }
}
