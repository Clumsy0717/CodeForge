package com.codeforge.mobile

import java.io.File

/**
 * Routes file extensions to language-specific execution paths.
 * Remote execution is only a client integration; the backend must securely
 * compile/run code in isolated environments with strict resource limits.
 */
sealed class LanguageAdapter {
    data object Python : LanguageAdapter()
    data object Php : LanguageAdapter()
    data object Cpp : LanguageAdapter()
    data object Web : LanguageAdapter()
    data object Unsupported : LanguageAdapter()

    companion object {
        fun forFile(file: File): LanguageAdapter = when (file.extension.lowercase()) {
            "py" -> Python
            "php" -> Php
            "cpp", "cc", "cxx", "c", "h", "hpp" -> Cpp
            "html", "htm", "css", "js" -> Web
            else -> Unsupported
        }
    }
}
