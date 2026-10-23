package com.codeforge.mobile

import android.text.Editable
import android.text.Spannable
import android.text.style.CharacterStyle
import android.text.TextPaint
import android.text.style.UpdateAppearance
import java.util.regex.Pattern

/**
 * Lightweight regex highlighter for common tokens. This is intentionally small;
 * it is not a parser or language server.
 */
object SyntaxHighlighter {

    private data class Rule(val pattern: Pattern, val color: Int)

    fun apply(editable: Editable, extension: String) {
        editable.getSpans(0, editable.length, SyntaxTokenSpan::class.java)
            .forEach(editable::removeSpan)

        val source = editable.toString()
        val rules = rulesFor(extension.lowercase())
        rules.forEach { rule ->
            val matcher = rule.pattern.matcher(source)
            while (matcher.find()) {
                if (matcher.start() < matcher.end()) {
                    editable.setSpan(
                        SyntaxTokenSpan(rule.color),
                        matcher.start(),
                        matcher.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }
    }

    private fun rulesFor(extension: String): List<Rule> {
        val keywordColor = 0xFFC586C0.toInt()
        val typeColor = 0xFF569CD6.toInt()
        val stringColor = 0xFFCE9178.toInt()
        val commentColor = 0xFF6A9955.toInt()
        val functionColor = 0xFFDCDCAA.toInt()

        val keywords = when (extension) {
            "py" -> "def|class|if|elif|else|for|while|return|import|from|as|try|except|finally|with|pass|break|continue|in|and|or|not|is|lambda|yield|async|await|True|False|None"
            "php" -> "function|class|if|else|elseif|foreach|while|return|echo|new|public|private|protected|static|namespace|use|try|catch|throw|extends|implements"
            "cpp", "cc", "cxx", "c", "h", "hpp" -> "int|float|double|char|void|bool|return|if|else|for|while|class|struct|public|private|protected|const|auto|include|using|namespace|std|switch|case|break|static|long|unsigned"
            "js", "mjs", "cjs" -> "function|class|const|let|var|if|else|for|while|return|new|async|await|import|export|from|default|try|catch|throw|true|false|null|undefined"
            "kt" -> "fun|val|var|class|object|if|else|when|for|while|return|package|import|private|public|internal|suspend|data|sealed|interface|override|null|true|false"
            "java" -> "class|interface|public|private|protected|static|void|int|double|float|boolean|new|return|if|else|for|while|package|import|extends|implements|try|catch|final"
            "css" -> "color|background|display|flex|grid|margin|padding|width|height|position|font-size|border"
            else -> ""
        }

        val rules = mutableListOf<Rule>()
        if (keywords.isNotBlank()) {
            rules += Rule(Pattern.compile("\\b($keywords)\\b"), keywordColor)
        }

        when (extension) {
            "py" -> rules += Rule(Pattern.compile("\\b(print|len|range|str|int|float|list|dict|open|super|enumerate)\\b"), functionColor)
            "html", "htm" -> rules += Rule(Pattern.compile("</?[A-Za-z][^>]*>"), typeColor)
            else -> Unit
        }

        if (extension in setOf("py", "php", "cpp", "cc", "cxx", "c", "h", "hpp", "js", "mjs", "cjs", "kt", "java", "html", "htm", "css")) {
            rules += Rule(Pattern.compile("\"([^\"\\\\\\\\]|\\\\\\\\.)*\"|'([^'\\\\\\\\]|\\\\\\\\.)*'"), stringColor)
        }

        when (extension) {
            "py" -> rules += Rule(Pattern.compile("#[^\\n]*"), commentColor)
            "php" -> rules += Rule(Pattern.compile("//[^\\n]*|#[^\\n]*|/\\*[\\s\\S]*?\\*/"), commentColor)
            "cpp", "cc", "cxx", "c", "h", "hpp", "js", "mjs", "cjs", "kt", "java", "css" ->
                rules += Rule(Pattern.compile("//[^\\n]*|/\\*[\\s\\S]*?\\*/"), commentColor)
            "html", "htm" -> rules += Rule(Pattern.compile("<!--[\\s\\S]*?-->"), commentColor)
        }
        return rules
    }

    private class SyntaxTokenSpan(private val color: Int) : CharacterStyle(), UpdateAppearance {
        override fun updateDrawState(tp: TextPaint) {
            tp.color = color
        }
    }
}
