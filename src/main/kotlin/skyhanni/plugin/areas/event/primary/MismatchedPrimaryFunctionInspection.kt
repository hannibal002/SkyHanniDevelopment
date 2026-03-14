package skyhanni.plugin.areas.event.primary

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import skyhanni.plugin.areas.event.HANDLE_EVENT_ANNOTATION
import skyhanni.plugin.areas.event.PRIMARY_FUNCTION_ANNOTATION
import skyhanni.plugin.areas.event.SKYHANNI_EVENT_FQN

/**
 * Warns when a @HandleEvent function specifies an explicit eventType but the function name
 * does not match the @PrimaryFunction name declared on that event class.
 *
 * For example, if SomeEvent has @PrimaryFunction("onSomething"), then:
 *   @HandleEvent(eventType = SomeEvent::class) fun onWrongName() -- is warned
 *   @HandleEvent(eventType = SomeEvent::class) fun onSomething() -- is fine
 *
 * Suppressable via @Suppress("MismatchedPrimaryFunctionName").
 */
class MismatchedPrimaryFunctionInspection : AbstractKotlinInspection() {

    override fun getDisplayName() = "Handler function name does not match @PrimaryFunction for the event"
    override fun getShortName() = "MismatchedPrimaryFunctionName"
    override fun getGroupDisplayName() = "SkyHanni"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor = object : KtVisitorVoid() {
        override fun visitNamedFunction(function: KtNamedFunction) {
            if (function.containingClassOrObject !is KtObjectDeclaration) return
            val functionName = function.name ?: return

            val annotation = function.annotationEntries
                .find { it.shortName?.asString() == HANDLE_EVENT_ANNOTATION } ?: return

            // Only check functions with explicit eventType - parameterless functions
            // with no explicit type are handled by HandleEventInspection
            val eventTypeArg = annotation.valueArguments.firstOrNull { arg ->
                val name = arg.getArgumentName()?.asName?.asString()
                name == "eventType" || name == "eventTypes" || name == null
            } ?: return

            val classLiteral = eventTypeArg.getArgumentExpression() as? KtClassLiteralExpression ?: return
            val explicitClassName = classLiteral.receiverExpression?.text?.substringAfterLast('.') ?: return

            val scope = GlobalSearchScope.allScope(function.project)
            val psiClass = PsiShortNamesCache.getInstance(function.project)
                .getClassesByName(explicitClassName, scope)
                .firstOrNull { InheritanceUtil.isInheritor(it, SKYHANNI_EVENT_FQN) } ?: return

            val ktClass = psiClass.navigationElement as? KtClassOrObject ?: return
            val primaryFunctionAnnotation = ktClass.annotationEntries
                .firstOrNull { it.shortName?.asString() == PRIMARY_FUNCTION_ANNOTATION } ?: return
            val expectedName = primaryFunctionAnnotation.valueArguments.firstOrNull()
                ?.getArgumentExpression()?.text?.removeSurrounding("\"")
                ?.takeIf { it.isNotBlank() } ?: return

            if (functionName == expectedName) return

            holder.registerProblem(
                function.nameIdentifier ?: return,
                "Handler name '$functionName' does not match @PrimaryFunction(\"$expectedName\") on $explicitClassName",
                ProblemHighlightType.WARNING,
            )
        }
    }
}