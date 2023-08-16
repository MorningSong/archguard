package org.archguard.scanner.core.sourcecode

import org.archguard.scanner.core.context.AnalyserType
import org.archguard.scanner.core.context.Context

interface SourceCodeContext : Context {
    override val type: AnalyserType get() = AnalyserType.SOURCE_CODE

    // TODO replace with DAG
    val language: String
    val features: List<String>
    val path: String
    val withFunctionCode: Boolean
    val debug: Boolean
}
