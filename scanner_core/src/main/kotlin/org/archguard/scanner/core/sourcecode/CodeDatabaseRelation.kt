package org.archguard.scanner.core.sourcecode

import kotlinx.serialization.Serializable
import org.archguard.scanner.core.diffchanges.NodeRelation

@Serializable
data class CodeDatabaseRelation(
    val packageName: String = "",
    val className: String = "",
    val functionName: String = "",
    val tables: List<String> = listOf(),
    val sqls: List<String> = listOf(),
    val implementations: List<String> = listOf(),
    var relations: List<NodeRelation> = listOf(),
) {
    fun relationBeautify(): String {
        val sb = StringBuilder()
        sb.append("{\n")
        relations.reversed().forEach {
            sb.append("   ${it.source} -> ${it.target}\n")
        }
        sb.append("}")

        return sb.toString()
    }
}

