package skyhanni.plugin.areas.config

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtProperty

const val CONFIG_OPTION_ANNOTATION = "ConfigOption"
const val PROPERTY_FQN = "io.github.notenoughupdates.moulconfig.observer.Property"

class ConvertConfigToPropertyIntention :
    SelfTargetingOffsetIndependentIntention<KtProperty>(
        KtProperty::class.java,
        { "Convert @ConfigOption to Property<T>" }
    ) {

    override fun isApplicableTo(element: KtProperty): Boolean =
        element.isVar &&
            element.annotationEntries.any { it.shortName?.asString() == CONFIG_OPTION_ANNOTATION } &&
            element.typeReference != null &&
            element.initializer != null

    override fun applyTo(element: KtProperty, editor: Editor?) {
        val factory = KtPsiFactory(element.project)

        // var → val
        element.valOrVarKeyword.replace(factory.createValKeyword())

        // T → Property<T>
        val oldType = element.typeReference!!
        oldType.replace(factory.createType("Property<${oldType.text}>"))

        // initializer → Property.of(initializer)
        val oldInit = element.initializer!!
        element.initializer = factory.createExpression("Property.of(${oldInit.text})")

        // Add import if not already present
        val file = element.containingKtFile
        if (file.importDirectives.none { it.importedFqName?.asString() == PROPERTY_FQN }) {
            val importDirective = factory.createFile("import $PROPERTY_FQN\n")
                .importDirectives
                .firstOrNull() ?: return
            (file.importList ?: file).add(importDirective)
        }
    }

    // Maybe can go?
    // SelfTargetingOffsetIndependentIntention automatically generates a preview
    // by running applyTo on a sandboxed copy of the file
    /*override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val element = file.findElementAt(editor.caretModel.offset)
            ?.getStrictParentOfType<KtProperty>()
            ?: return IntentionPreviewInfo.EMPTY

        // Guard: preview requires type and initializer to be present
        val typeText = element.typeReference?.text ?: return IntentionPreviewInfo.EMPTY
        val initText = element.initializer?.text ?: return IntentionPreviewInfo.EMPTY
        val modifiers = element.modifierList?.text?.let { "$it " } ?: ""
        val name = element.name ?: return IntentionPreviewInfo.EMPTY

        val before = "${modifiers}var $name: $typeText = $initText"
        val after = "${modifiers}val $name: Property<$typeText> = Property.of($initText)"

        return IntentionPreviewInfo.CustomDiff(file.fileType, before, after)
    }*/
}
