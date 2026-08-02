package skyhanni.plugin.areas.mixin

import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter

private const val MIXIN_ANNOTATION = "Mixin"

private val INJECTOR_ANNOTATIONS = setOf(
    "Inject",
    "WrapOperation",
    "ModifyArg",
    "ModifyVariable",
    "WrapMethod",
    "ModifyReturnValue",
    "ModifyExpressionValue",
    "WrapWithCondition",
    "Redirect",
    "ModifyArgs",
    "ModifyConstant",
    "Overwrite",
)

/**
 *
 * Mixins are never referenced from code, and the Minecraft Development plugin cannot recognize them
 * either, because SkyHanni generates the mixin configuration at compile time via KSP instead of
 * shipping a static mixin config file.
 */
class MixinImplicitUsageProvider : ImplicitUsageProvider {

    override fun isImplicitUsage(element: PsiElement): Boolean = when (element) {
        is PsiClass -> element.hasAnnotationNamed(MIXIN_ANNOTATION)
        is PsiMethod -> element.isMixinInjector()
        is PsiParameter -> (element.declarationScope as? PsiMethod)?.isMixinInjector() == true

        else -> false
    }

    override fun isImplicitRead(element: PsiElement): Boolean = false

    override fun isImplicitWrite(element: PsiElement): Boolean = false
}

private fun PsiMethod.isMixinInjector(): Boolean =
    containingClass?.hasAnnotationNamed(MIXIN_ANNOTATION) == true &&
        INJECTOR_ANNOTATIONS.any { hasAnnotationNamed(it) }

/**
 * Matches on the simple annotation name, so the check also works while the project is not fully
 * resolved. Injector names are only accepted inside a class annotated with `@Mixin`, which keeps
 * unrelated `@Inject` annotations from other frameworks out.
 */
private fun PsiModifierListOwner.hasAnnotationNamed(shortName: String): Boolean =
    annotations.any { it.qualifiedName?.substringAfterLast('.') == shortName }
