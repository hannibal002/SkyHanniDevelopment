package skyhanni.plugin.areas.config

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class ConfigPathReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
            ConfigPathReferenceProvider()
        )
    }
}

private class ConfigPathReferenceProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext
    ): Array<PsiReference> {
        val str = element as? KtStringTemplateExpression ?: return PsiReference.EMPTY_ARRAY
        val literal = str.text.removeSurrounding("\"")
        if (!literal.contains('.')) return PsiReference.EMPTY_ARRAY

        // Only activate inside event.move("...") calls
        val call = PsiTreeUtil.getParentOfType(str, KtCallExpression::class.java)
            ?: return PsiReference.EMPTY_ARRAY
        val dot = call.parent as? KtDotQualifiedExpression ?: return PsiReference.EMPTY_ARRAY
        if (dot.receiverExpression.text != "event" || call.calleeExpression?.text != "move") {
            return PsiReference.EMPTY_ARRAY
        }

        return arrayOf(ConfigPathReference(str))
    }
}

private class ConfigPathReference(
    element: KtStringTemplateExpression,
) : PsiReferenceBase<KtStringTemplateExpression>(element, TextRange(1, element.textLength - 1), true) {

    override fun resolve(): PsiElement? {
        val path = element.text.removeSurrounding("\"")
        val segments = path.split('.').toMutableList().takeIf { it.isNotEmpty() } ?: return null
        val project = element.project

        val rootClassName = when (segments.first()) {
            "#profile" -> { segments.removeFirst(); PROFILE_STORAGE_CLASS }
            "#player"  -> { segments.removeFirst(); PLAYER_STORAGE_CLASS }
            else       -> BASE_CONFIG_CLASS
        }

        var current = findKtClass(rootClassName) ?: return null

        for ((i, name) in segments.withIndex()) {
            val prop = current.declarations
                .filterIsInstance<KtProperty>()
                .firstOrNull { it.name == name }
                ?: return null

            if (i == segments.lastIndex) return prop.navigationElement

            val typeText = prop.typeReference?.text ?: return null
            if (i == segments.lastIndex - 1 &&
                (typeText.startsWith("MutableMap") || typeText.startsWith("Map"))
            ) return prop.navigationElement

            val rawType = typeText.substringBefore('<').substringBefore('?').ifEmpty { return null }
            val scope = GlobalSearchScope.projectScope(project)
            val nextClass = PsiShortNamesCache.getInstance(project)
                .getClassesByName(rawType, scope)
                .firstOrNull { it.qualifiedName?.startsWith(BASE_CONFIG_PKG) == true }
                ?: return null

            current = nextClass.navigationElement as? KtClassOrObject ?: return null
        }
        return null
    }

    private fun findKtClass(fqName: String): KtClassOrObject? =
        JavaPsiFacade.getInstance(element.project)
            .findClass(fqName, GlobalSearchScope.projectScope(element.project))
            ?.navigationElement as? KtClassOrObject

    // Suppress rename refactoring — this is a config path, not a symbol reference
    override fun handleElementRename(newElementName: String) = element
}