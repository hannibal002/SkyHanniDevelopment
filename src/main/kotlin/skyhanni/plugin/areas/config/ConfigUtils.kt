package skyhanni.plugin.areas.config


import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtProperty
import kotlin.collections.setOf

const val CONFIG_OPTION_ANNOTATION = "ConfigOption"

const val BASE_CONFIG_PKG = "at.hannibal2.skyhanni.config"
const val BASE_CONFIG_CLASS = "at.hannibal2.skyhanni.config.Features"
const val PROFILE_STORAGE_CLASS = "at.hannibal2.skyhanni.config.storage.ProfileSpecificStorage"
const val PLAYER_STORAGE_CLASS = "at.hannibal2.skyhanni.config.storage.PlayerSpecificStorage"

const val PROPERTY_FQN = "io.github.notenoughupdates.moulconfig.observer.Property"

const val NOTIFICATION_GROUP = "SkyHanni Plugin"

/** FQNs that are considered config roots — traversal stops when one is reached. */
val ROOT_CONFIG_FQNS = setOf(BASE_CONFIG_CLASS, PROFILE_STORAGE_CLASS, PLAYER_STORAGE_CLASS)

/**
 * The "modules.editor." prefix that appears in the raw path but should be
 * stripped from the user-visible path.
 */
const val STRIP_PREFIX = "modules.editor."

/**
 * Walks up the config class hierarchy via reverse reference search,
 * building a dotted path from the nearest root class down to [prop].
 *
 * Returns e.g. `"inventory.items.slot"` with [STRIP_PREFIX] removed,
 * or `null` if the property name is unavailable.
 */
fun computeConfigPath(prop: KtProperty): String? {
    val project = prop.project
    val segments = mutableListOf<String>()
    segments.add(prop.name ?: return null)

    var currentClass = PsiTreeUtil.getParentOfType(prop, KtClassOrObject::class.java)
    while (currentClass != null) {
        if (currentClass.fqName?.asString() in ROOT_CONFIG_FQNS) break
        val (propertyName, parentClass) = findContainingProperty(currentClass, project) ?: break
        segments.add(propertyName)
        currentClass = parentClass
    }

    segments.reverse()
    return segments.joinToString(".").removePrefix(STRIP_PREFIX).takeIf { it.isNotEmpty() }
}

/**
 * Given a config class, finds the property in another class whose type
 * references this class — i.e. walks one level up the config tree.
 *
 * Returns `Pair(propertyName, containingClass)`, or `null` if at the root.
 */
fun findContainingProperty(
    kClass: KtClassOrObject,
    project: Project,
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