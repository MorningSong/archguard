package com.thoughtworks.archgard.scanner.domain.project

import java.io.File

data class CompiledProject(val gitRepo: String, val workspace: File, val buildTool: BuildTool, val sql: String?)