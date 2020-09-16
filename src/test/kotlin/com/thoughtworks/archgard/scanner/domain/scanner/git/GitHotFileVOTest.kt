package com.thoughtworks.archgard.scanner.domain.scanner.git

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class GitHotFileVOTest {
    @Test
    internal fun shouldReturnFalseWhenCheckIfJVMClassGivenNonJVMFile() {
        val gitHotFile = GitHotFileVO("README.MD", 10)

        assertFalse(gitHotFile.isJVMClass())
    }

    @Test
    internal fun shouldReturnTrueWhenCheckIfJVMClassGivenJavaFile() {
        val gitHotFile = GitHotFileVO("src/main/java/com/qicaisheng/parkinglot/HTMLReportVisitor.java", 10)
        
        assertTrue(gitHotFile.isJVMClass())
    }

    @Test
    internal fun shouldReturnTrueWhenCheckIfJVMClassGivenKotlinFile() {
        val gitHotFile = GitHotFileVO("src/main/kotlin/com/qicaisheng/parkinglot/HTMLReportVisitor.kt", 10)

        assertTrue(gitHotFile.isJVMClass())
    }

    @Test
    internal fun shouldGetClassNameGivenJavaFile() {
        val gitHotFile = GitHotFileVO("src/main/java/com/qicaisheng/parkinglot/HTMLReportVisitor.java", 10)

        assertEquals("com.qicaisheng.parkinglot.HTMLReportVisitor", gitHotFile.className())
    }

    @Test
    internal fun shouldGetClassNameGivenKotlinFile() {
        val gitHotFile = GitHotFileVO("src/main/kotlin/com/qicaisheng/parkinglot/HTMLReportVisitor.kt", 10)

        assertEquals("com.qicaisheng.parkinglot.HTMLReportVisitor", gitHotFile.className())
    }

    @Test
    internal fun shouldGetNullClassNameGivenNonJVMFile() {
        val gitHotFile = GitHotFileVO("README.MD", 10)

        assertEquals(null, gitHotFile.className())
    }

    @Test
    internal fun shouldGetNullModuleNameGivenJVMFileInSrcTopDirection() {
        val gitHotFile = GitHotFileVO("src/main/kotlin/com/qicaisheng/parkinglot/HTMLReportVisitor.kt", 10)

        assertNull(gitHotFile.moduleName())
    }

    @Test
    internal fun shouldGetModuleNameGivenJVMFileInModuleSrcTopDirection() {
        val gitHotFile = GitHotFileVO("dubbo-samples-zookeeper/src/main/java/org/apache/dubbo/samples/action/GreetingServiceConsumer.java", 10)

        assertEquals("dubbo-samples-zookeeper", gitHotFile.moduleName())
    }

}