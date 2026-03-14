package skyhanni.plugin.areas.event

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Warns on concrete (non-abstract) SkyHanniEvent subclasses that have no @PrimaryFunction annotation.
 * These events can only be handled via explicit eventType = ... in @HandleEvent, which is verbose.
 * Adding @PrimaryFunction enables parameterless handlers and cleaner code.
 *
 * Where a candidate name can be derived and is not already taken, a quick fix is offered.
 *
 * Suppressable via @Suppress("MissingPrimaryFunction").
 */
class MissingPrimaryFunctionInspection : AbstractKotlinInspection() {

    override fun getDisplayName() = "Event should have @PrimaryFunction or be abstract"
    override fun getShortName() = "MissingPrimaryFunction"
    override fun getGroupDisplayName() = "SkyHanni"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor = object : KtVisitorVoid() {
        override fun visitClass(klass: KtClass) {
            if (klass.hasModifier(KtTokens.ABSTRACT_KEYWORD)) return
            if (klass.isInterface()) return
            if (klass.annotationEntries.any { it.shortName?.asString() == PRIMARY_FUNCTION_ANNOTATION }) return

            val fqName = klass.fqName?.asString() ?: return
            val project = klass.project
            val psiClass = JavaPsiFacade.getInstance(project)
                .findClass(fqName, GlobalSearchScope.allScope(project)) ?: return
            if (!InheritanceUtil.isInheritor(psiClass, SKYHANNI_EVENT_FQN)) return

            // Prefer the enclosing class name for nested events (e.g. SomeEvent.Allow → onSomeEvent)
            val enclosing = PsiTreeUtil.getParentOfType(klass, KtClassOrObject::class.java)
            val baseName = (enclosing?.name ?: klass.name) ?: return
            val stripped = if (baseName.endsWith("Event")) baseName.dropLast(5) else baseName
            val candidate = "on$stripped"

            val fix = if (candidate !in buildPrimaryNameMap(project).keys) AddPrimaryFunctionFix(candidate) else null

            holder.registerProblem(
                klass.nameIdentifier ?: return,
                "Event should either have a @PrimaryFunction, or be abstract",
                ProblemHighlightType.WEAK_WARNING,
                *listOfNotNull(fix).toTypedArray(),
            )
        }
    }

    private class AddPrimaryFunctionFix(private val name: String) : LocalQuickFix {
        override fun getName() = "Add @PrimaryFunction(\"$name\")"
        override fun getFamilyName() = "Add @PrimaryFunction"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val klass = descriptor.psiElement.parent as? KtClass ?: return
            val annotation = KtPsiFactory(project).createAnnotationEntry("@$PRIMARY_FUNCTION_ANNOTATION(\"$name\")")
            klass.addAnnotationEntry(annotation)
        }
    }
}