package skyhanni.plugin.areas.config

import com.intellij.codeInsight.hints.declarative.EndOfLinePosition
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtProperty

import com.intellij.codeInsight.hints.declarative.InlayHintsProvider as DeclarativeInlayHintsProvider

class ConfigPathInlayHintsProvider : DeclarativeInlayHintsProvider {

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector =
        object : SharedBypassCollector {
            override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
                val property = element as? KtProperty ?: return
                if (property.annotationEntries.none { it.shortName?.asString() == CONFIG_OPTION_ANNOTATION }) return

                val path = computeConfigPath(property) ?: return
                val varLine = editor.document.getLineNumber(
                    property.valOrVarKeyword.textRange.startOffset
                )

                sink.addPresentation(
                    position = EndOfLinePosition(varLine),
                    hintFormat = HintFormat.default,
                ) {
                    text(path)
                }
            }
        }
}
