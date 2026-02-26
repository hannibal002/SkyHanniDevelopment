package skyhanni.plugin.areas.misc

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val REGEX_TEST_PREFIX = "REGEX-TEST: "
private const val REGEX_FAIL_PREFIX = "REGEX-FAIL: "
private const val WRAPPED_REGEX_TEST_PREFIX = "WRAPPED-REGEX-TEST:"
private val WRAPPED_REGEX_TEST_PATTERN = "WRAPPED-REGEX-TEST: \"(?<test>.*)\"".toPattern()

/**
 * Adds a gutter icon next to every `pattern(key, regex)` call that opens
 * the regex on regex101.com with REGEX-TEST examples pre-populated.
 * Only shown when the regex is a plain string literal (no interpolation).
 */
class Regex101LineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // We want to anchor to the `pattern` callee identifier
        val nameExpr = element as? KtSimpleNameExpression ?: return null
        if (nameExpr.getReferencedName() != "pattern") return null

        val call = nameExpr.parent as? KtCallExpression ?: return null
        if (call.valueArguments.size != 2) return null

        val property = call.getParentOfType<KtProperty>(strict = true) ?: return null
        val regexArg = call.valueArguments[1] ?: return null
        val info = RegexInfo(regexArg, property.docComment)

        val regexText = info.getRegexText()?.replace("\"", "\\\"") ?: return null
        val examples = info.getExamples()
        val exampleCount = examples.size

        val url = buildUrl(regexText, examples)
        val tooltip = "Open on regex101.com" +
                if (exampleCount > 0) " ($exampleCount example${if (exampleCount == 1) "" else "s"})" else ""

        return LineMarkerInfo(
            element,
            element.textRange,
            AllIcons.General.Web,
            { tooltip },
            { _, _ -> BrowserUtil.browse(url) },
            GutterIconRenderer.Alignment.LEFT,
            { tooltip }
        )
    }

    private fun buildUrl(regex: String, examples: List<String>): String {
        val testString = examples.joinToString("\n")
        return "https://regex101.com/?" +
                "regex=${encode(regex)}" +
                "&testString=${encode(testString)}" +
                "&flavor=java"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}

private class RegexInfo(
    private val regexArg: KtValueArgument,
    private val comment: KDoc?,
) {
    fun getRegexText(): String? {
        val template = regexArg.getArgumentExpression() as? KtStringTemplateExpression ?: return null
        val sb = StringBuilder()
        for (entry in template.entries) {
            when (entry) {
                is KtEscapeStringTemplateEntry -> sb.append(entry.unescapedValue)
                is KtLiteralStringTemplateEntry -> sb.append(entry.text)
                else -> return null
            }
        }
        return sb.toString()
    }

    fun getExamples(): List<String> = buildList {
        val lines = commentText ?: return@buildList
        lines.filter { it.startsWith(REGEX_TEST_PREFIX) || it.startsWith(REGEX_FAIL_PREFIX) }
            .map { it.substring(REGEX_TEST_PREFIX.length) }
            .let(::addAll)
        lines.filter { it.startsWith(WRAPPED_REGEX_TEST_PREFIX) }
            .mapNotNull { line ->
                val matcher = WRAPPED_REGEX_TEST_PATTERN.matcher(line)
                if (matcher.matches()) matcher.group("test") else null
            }
            .let(::addAll)
    }

    private val commentText: List<String>? by lazy {
        comment?.text
            ?.replace("/*", "")
            ?.replace("*/", "")
            ?.lines()
            ?.map { it.trim().trimStart('*').trim() }
    }
}