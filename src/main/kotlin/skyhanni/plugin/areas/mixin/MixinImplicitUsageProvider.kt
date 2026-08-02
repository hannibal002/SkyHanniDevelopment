package skyhanni.plugin.areas.mixin

import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter

private const val MIXIN_ANNOTATION = "Mixin"

private const val SHADOW_ANNOTATION = "Shadow"

private const val UNUSED_TOOL_ID = "unused"

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
 * Marks mixin classes, their injector methods, the parameters of those methods and their shadowed
 * members as used. Shadowed fields are also treated as written, because the value is assigned by the
 * class the mixin is applied to.
 *
 * Mixins are never referenced from code, and the Minecraft Development plugin cannot recognize them
 * either, because SkyHanni generates the mixin configuration at compile time via KSP instead of
 * shipping a static mixin config file.
 */
class MixinImplicitUsageProvider : ImplicitUsageProvider {

    override fun isImplicitUsage(element: PsiElement): Boolean = when (element) {
        is PsiClass -> element.hasAnnotationNamed(MIXIN_ANNOTATION)
        is PsiMethod -> element.isMixinInjector() || element.isMixinShadow()
        is PsiField -> element.isMixinShadow()
        is PsiParameter -> (element.declarationScope as? PsiMethod)?.isMixinInjector() == true

        else -> false
    }

    override fun isImplicitRead(element: PsiElement): Boolean = false

    override fun isImplicitWrite(element: PsiElement): Boolean = element is PsiField && element.isMixinShadow()
}

/**
 * Suppresses unused parameter warnings inside mixin injector methods. Their signature is dictated by
 * the injection point, so parameters like `CallbackInfo` cannot be left out even when unused.
 *
 * [MixinImplicitUsageProvider] cannot cover this, because the Java highlighting pass does not
 * consult implicit usage providers when it checks parameters.
 */
class MixinInspectionSuppressor : InspectionSuppressor {

    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId != UNUSED_TOOL_ID) return false
        val parameter = element as? PsiParameter ?: element.parent as? PsiParameter ?: return false
        return (parameter.declarationScope as? PsiMethod)?.isMixinInjector() == true
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> =
        SuppressQuickFix.EMPTY_ARRAY
}

private fun PsiMember.isInMixinClass(): Boolean = containingClass?.hasAnnotationNamed(MIXIN_ANNOTATION) == true

private fun PsiMethod.isMixinInjector(): Boolean = isInMixinClass() && INJECTOR_ANNOTATIONS.any { hasAnnotationNamed(it) }

private fun PsiMember.isMixinShadow(): Boolean = isInMixinClass() && hasAnnotationNamed(SHADOW_ANNOTATION)

/**
 * Matches on the simple annotation name, so the check also works while the project is not fully
 * resolved. Injector names are only accepted inside a class annotated with `@Mixin`, which keeps
 * unrelated `@Inject` annotations from other frameworks out.
 */
private fun PsiModifierListOwner.hasAnnotationNamed(shortName: String): Boolean =
    annotations.any { it.qualifiedName?.substringAfterLast('.') == shortName }
