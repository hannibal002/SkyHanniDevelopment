package skyhanni.plugin.areas.config

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Activates on string literals inside `event.move("some.dotted.path")` calls.
 * Navigates through the config class hierarchy to the referenced property.
 *
 * Prefix rules:
 *   "#profile.x.y" → starts from ProfileSpecificStorage
 *   "#player.x.y" → starts from PlayerSpecificStorage
 *   "x.y" → starts from Features (default)
 */
class NavigateToConfigIntention :
    SelfTargetingOffsetIndependentIntention<KtStringTemplateExpression>(
        KtStringTemplateExpression::class.java,
        { "Go to config definition" }
    ) {

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY

    override fun isApplicableTo(element: KtStringTemplateExpression): Boolean {
        val literal = element.text.removeSurrounding("\"")
        if (!literal.contains('.')) return false

        // Only activate inside event.move("...") calls
        val call = PsiTreeUtil.getParentOfType(element, KtCallExpression::class.java) ?: return false
        val dot = call.parent as? KtDotQualifiedExpression ?: return false
        return dot.receiverExpression.text == "event" && call.calleeExpression?.text == "move"
    }

    override fun applyTo(element: KtStringTemplateExpression, editor: Editor?) {
        val project = element.project
        val path = element.text.removeSurrounding("\"")
        val segments = path.split('.').toMutableList().takeIf { it.isNotEmpty() } ?: return

        // Resolve the root class, consuming prefix segments where applicable
        val rootClassName = when (segments.first()) {
            "#profile" -> { segments.removeFirst(); PROFILE_STORAGE_CLASS }
            "#player"  -> { segments.removeFirst(); PLAYER_STORAGE_CLASS }
            else -> BASE_CONFIG_CLASS
        }

        var current = findKtClass(element, rootClassName) ?: run {
            warn(project, "Could not find root class '$rootClassName' for path '$path'")
            return
        }

        for ((i, name) in segments.withIndex()) {
            val prop = current.declarations
                .filterIsInstance<KtProperty>()
                .firstOrNull { it.name == name }
                ?: run {
                    warn(project, "Property '$name' not found in ${current.name} for path '$path'")
                    return
                }

            // Last segment — navigate directly to the property
            if (i == segments.lastIndex) {
                (prop.navigationElement as? NavigatablePsiElement)?.navigate(true)
                return
            }

            // Second-to-last and it's a Map — navigate to the map property itself
            val typeText = prop.typeReference?.text ?: ""
            if (i == segments.lastIndex - 1 &&
                (typeText.startsWith("MutableMap") || typeText.startsWith("Map"))
            ) {
                (prop.navigationElement as? NavigatablePsiElement)?.navigate(true)
                return
            }

            // Resolve the type to the next class in the chain
            val rawType = typeText.substringBefore('<').substringBefore('?').ifEmpty {
                warn(project, "Could not parse type of '${prop.name}' in ${current.name}")
                return
            }

            val scope = GlobalSearchScope.projectScope(project)
            val nextClass = PsiShortNamesCache.getInstance(project)
                .getClassesByName(rawType, scope)
                .firstOrNull { it.qualifiedName?.startsWith(BASE_CONFIG_PKG) == true }
                ?: run {
                    warn(project, "Config class '$rawType' not found under '$BASE_CONFIG_PKG'")
                    return
                }

            current = nextClass.navigationElement as? KtClassOrObject ?: run {
                warn(project, "Navigation target for '$rawType' is not a Kotlin class")
                return
            }
        }
    }

    private fun findKtClass(element: KtStringTemplateExpression, fqName: String): KtClassOrObject? =
        JavaPsiFacade.getInstance(element.project)
            .findClass(fqName, GlobalSearchScope.projectScope(element.project))
            ?.navigationElement as? KtClassOrObject

    private fun warn(project: Project, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification("⚠️ Navigate to config: $message", NotificationType.WARNING)
            .notify(project)
    }
}
