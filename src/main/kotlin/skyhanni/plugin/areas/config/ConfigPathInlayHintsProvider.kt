package skyhanni.plugin.areas.config

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtProperty
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Renders a clickable end-of-line config path hint for every non-abstract `@ConfigOption` property.
 * Each dot-separated segment is individually clickable and navigates to its definition.
 *
 * **plugin.xml**: register under `com.intellij.codeInsight.hints.inlayProvider`.
 */
@Suppress("UnstableApiUsage")
class ConfigPathInlayHintsProvider : InlayHintsProvider<NoSettings> {

    override val key = SettingsKey<NoSettings>("skyhanni.config.path")
    override val name = "Config path"
    override val previewText = null

    override fun createSettings() = NoSettings()
    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
        override fun createComponent(listener: ChangeListener): JComponent = JPanel()
    }

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink,
    ): InlayHintsCollector = object : FactoryInlayHintsCollector(editor) {
        override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
            val property = element as? KtProperty ?: return true
            if (property.annotationEntries.none { it.shortName?.asString() == CONFIG_OPTION_ANNOTATION }) return true
            val containingClass = PsiTreeUtil.getParentOfType(property, KtClassOrObject::class.java) ?: return true
            if (containingClass.isAbstract()) return true

            val segments = computeConfigPathSegments(property) ?: return true
            val presentation = buildPathPresentation(segments)

            val varLine = editor.document.getLineNumber(property.valOrVarKeyword.textRange.startOffset)
            sink.addInlineElement(editor.document.getLineEndOffset(varLine), true, presentation, false)
            return true
        }

        private fun buildPathPresentation(segments: List<ConfigPathSegment>): InlayPresentation {
            val dot = factory.smallText(".")
            val parts = segments.flatMapIndexed { i, segment ->
                val text = factory.smallText(segment.name)
                val navigable = segment.target as? NavigatablePsiElement
                val part = if (navigable != null) clickable(text, navigable) else text
                if (i < segments.lastIndex) listOf(part, dot) else listOf(part)
            }
            return factory.seq(*parts.toTypedArray())
        }

        private fun clickable(base: InlayPresentation, target: NavigatablePsiElement): InlayPresentation =
            factory.referenceOnHover(base
            ) { _, _ -> target.navigate(true) }
    }
}