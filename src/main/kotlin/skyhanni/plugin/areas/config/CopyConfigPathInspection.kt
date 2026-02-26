package skyhanni.plugin.areas.config

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid
import java.awt.datatransfer.StringSelection

const val STRIP_PREFIX = "modules.editor."

class CopyConfigPathInspection : AbstractKotlinInspection() {

    override fun getDisplayName() = "Copy config path for a @ConfigOption property"
    override fun getShortName() = "CopyConfigPath"
    override fun getGroupDisplayName() = "SkyHanni"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitProperty(property: KtProperty) {
                if (property.annotationEntries.any { it.shortName?.asString() == CONFIG_OPTION_ANNOTATION }) {
                    holder.registerProblem(
                        property.nameIdentifier ?: property,
                        "Copy config path",
                        ProblemHighlightType.INFORMATION,
                        CopyConfigPathFix()
                    )
                }
            }
        }
}

private class CopyConfigPathFix : LocalQuickFix {

    override fun getName() = "Copy config path"
    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val prop = PsiTreeUtil.getParentOfType(descriptor.psiElement, KtProperty::class.java) ?: return
        val path = computePath(prop, project).takeIf { it.isNotEmpty() } ?: return

        CopyPasteManager.getInstance().setContents(StringSelection(path))

        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification("Copied: $path", NotificationType.INFORMATION)
            .notify(project)
    }

    /**
     * Walks up the config class hierarchy via reverse reference search,
     * building a dotted path from the root config class down to this property.
     * e.g. "inventory.items.slot" with "modules.editor." stripped from the front.
     */
    private fun computePath(prop: KtProperty, project: Project): String {
        val segments = mutableListOf<String>()
        segments.add(prop.name ?: return "")

        var currentClass = PsiTreeUtil.getParentOfType(prop, KtClassOrObject::class.java)
        while (currentClass != null) {
            val (propertyName, parentClass) = findContainingProperty(currentClass, project) ?: break
            segments.add(propertyName)
            currentClass = parentClass
        }

        segments.reverse()
        return segments.joinToString(".").removePrefix(STRIP_PREFIX)
    }

    /**
     * Given a config class, finds the property in another class whose type
     * references this class — i.e. walks one level up the config tree.
     * Returns Pair(propertyName, containingClass) or null if at the root.
     */
    private fun findContainingProperty(
        kClass: KtClassOrObject,
        project: Project
    ): Pair<String, KtClassOrObject>? {
        val fqName = kClass.fqName?.asString() ?: return null
        val psiClass = JavaPsiFacade.getInstance(project)
            .findClass(fqName, GlobalSearchScope.allScope(project))
            ?: return null

        for (ref in ReferencesSearch.search(psiClass, GlobalSearchScope.projectScope(project)).findAll()) {
            val prop = PsiTreeUtil.getParentOfType(ref.element, KtProperty::class.java) ?: continue
            val parentClass = PsiTreeUtil.getParentOfType(prop, KtClassOrObject::class.java) ?: continue
            if (parentClass == kClass) continue
            return Pair(prop.name ?: continue, parentClass)
        }
        return null
    }
}
