package skyhanni.plugin.areas.event

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.kotlin.psi.KtClassOrObject

const val SKYHANNI_EVENT_FQN = "at.hannibal2.skyhanni.api.event.SkyHanniEvent"
const val HANDLE_EVENT_ANNOTATION = "HandleEvent"
const val PRIMARY_FUNCTION_ANNOTATION = "PrimaryFunction"

/**
 * Searches all classes inheriting from SkyHanniEvent and builds a map of
 * primary function name → fully-qualified event class name, sourced from
 * the @PrimaryFunction annotation on each event class.
 *
 * This is cached per-call by the caller; for heavier caching, a
 * project-level service could be introduced later.
 */
fun buildPrimaryNameMap(project: Project): Map<String, String> {
    val facade = JavaPsiFacade.getInstance(project)
    val skyHanniEventClass = facade.findClass(
        SKYHANNI_EVENT_FQN,
        GlobalSearchScope.allScope(project)
    ) ?: return emptyMap()

    return ClassInheritorsSearch
        .search(skyHanniEventClass, GlobalSearchScope.allScope(project), true, true, false)
        .mapNotNull { psiInheritor ->
            val ktClass = psiInheritor.navigationElement as? KtClassOrObject ?: return@mapNotNull null
            val annotation = ktClass.annotationEntries
                .firstOrNull { it.shortName?.asString() == PRIMARY_FUNCTION_ANNOTATION }
                ?: return@mapNotNull null

            val name = annotation.valueArguments
                .firstOrNull()
                ?.getArgumentExpression()
                ?.text
                ?.removeSurrounding("\"")
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null

            val fqn = ktClass.fqName?.asString() ?: return@mapNotNull null
            name to fqn
        }
        .toMap()
}
