package org.archguard.scanner.analyser.count

import java.io.File
import java.security.MessageDigest
import kotlin.experimental.and

class CodeStateTransition(
    val index: Int = 0,
    val state: CodeState = CodeState.BLANK,
    val endString: ByteArray = byteArrayOf(),
    val endComments: Array<ByteArray> = arrayOf(),
    val ignoreEscape: Boolean = false,
)

enum class CodeState {
    BLANK,
    CODE,
    COMMENT,
    COMMENT_CODE,
    MULTICOMMENT,
    MULTICOMMENT_CODE,
    MULTICOMMENT_BLANK,
    STRING,
    DOC_STRING;

    fun int(): Int {
        return ordinal + 1
    }
}

val byteOrderMarks = arrayOf(
    byteArrayOf(254.toByte(), 255.toByte()), // UTF-16 BE
    byteArrayOf(255.toByte(), 254.toByte()), // UTF-16 LE
    byteArrayOf(0, 0, 254.toByte(), 255.toByte()), // UTF-32 BE
    byteArrayOf(255.toByte(), 254.toByte(), 0, 0), // UTF-32 LE
    byteArrayOf(43, 47, 118, 56), // UTF-7
    byteArrayOf(43, 47, 118, 57), // UTF-7
    byteArrayOf(43, 47, 118, 43), // UTF-7
    byteArrayOf(43, 47, 118, 47), // UTF-7
    byteArrayOf(43, 47, 118, 56, 45), // UTF-7
    byteArrayOf(247.toByte(), 100, 76), // UTF-1
    byteArrayOf(221.toByte(), 115, 102, 115), // UTF-EBCDIC
    byteArrayOf(14, 254.toByte(), 255.toByte()), // SCSU
    byteArrayOf(251.toByte(), 238.toByte(), 40), // BOCU-1
    byteArrayOf(132.toByte(), 49, 149.toByte(), 51), // GB-18030
)

class LanguageWorker {
    private val languageService: LanguageService = LanguageService()

    // Duplicates enables duplicate file detection
    private var isDuplicates = false

    fun processFile(file: File): FileJob? {
        val fileJob = createFileJob(file)
        return processFile(fileJob)
    }

    fun processFile(fileJob: FileJob): FileJob? {
        fileJob.possibleLanguages = languageService.detectLanguages(fileJob.filename)
        if (fileJob.possibleLanguages.isEmpty()) return null

        fileJob.language =
            languageService.determineLanguage(fileJob.possibleLanguages[0], fileJob.possibleLanguages, fileJob.content)

        return countStats(fileJob)
    }

    /**
     * Process code as a string.
     */
    fun processCode(code: String, filename: String): FileJob? {
        val fileContent = code.toByteArray()
        val fileJob = FileJob(
            content = fileContent,
            filename = filename,
            bytes = fileContent.size.toLong(),
        )

        return processFile(fileJob)
    }

    /**
     * Count single FileJob stats.
     *
     * for examples:
     * ```kotlin
     * """  // Comment 1
     * namespace Baz
     * {
     *     using System;
     *
     *     public class FooClass
     *     {
     *         public void Test(string report)
     *         {
     *           // Comment 2
     *           throw new NotImplementedException();
     *         }
     *     }
     * }""".toByteArray()
     *  val job = FileJob(
     *      language = "C#",
     *      content = content,
     *      bytes = content.size.toLong(),
     *  )
     *
     *  worker.countStats(job)!!
     *
     *  job.lines shouldBe 14
     *  job.code shouldBe 11
     *  job.comment shouldBe 2
     *  job.blank shouldBe 1
     * ```
     */
    fun countStats(fileJob: FileJob): FileJob? {
        val langFeatures = languageService.getLanguageFeature(fileJob.language) ?: return null

        if (langFeatures.complexity == null) langFeatures.complexity = Trie()
        if (langFeatures.singleLineComments == null) langFeatures.singleLineComments = Trie()
        if (langFeatures.multiLineComments == null) langFeatures.multiLineComments = Trie()
        if (langFeatures.strings == null) langFeatures.strings = Trie()
        if (langFeatures.tokens == null) langFeatures.tokens = Trie()

        val endPoint: Int = (fileJob.bytes - 1).toInt()
        var currentState: CodeState = CodeState.BLANK
        var endComments: Array<ByteArray> = arrayOf()
        var endString = byteArrayOf()

        // TODO needs to be set via langFeatures.Quotes[0].IgnoreEscape for the matching feature
        var ignoreEscape = false;

        val first = checkBomSkip(fileJob)
        var index = first;
        val fileLen = fileJob.bytes.toInt()
        while (index in first until fileLen) {
            // Based on our current state determine if the state should change by checking
            // what the character is. The below is very CPU bound so need to be careful if
            // changing anything in here and profile/measure afterwards!
            // NB that the order of the if statements matters and has been set to what in benchmarks is most efficient
            if (!LanguageService.isWhitespace(fileJob.content[index])) {
                when (currentState) {
                    CodeState.CODE -> {
                        val codeStateTransition = codeState(
                            fileJob,
                            index,
                            endPoint,
                            currentState,
                            endString,
                            endComments,
                            langFeatures,
                            fileJob.hash
                        )
                        index = codeStateTransition.index
                        currentState = codeStateTransition.state
                        endString = codeStateTransition.endString
                        endComments = codeStateTransition.endComments
                        ignoreEscape = codeStateTransition.ignoreEscape
                    }

                    CodeState.STRING -> {
                        val stringStateTransition = stringState(
                            fileJob,
                            index,
                            endPoint,
                            endString,
                            currentState,
                            ignoreEscape
                        )
                        index = stringStateTransition.index
                        currentState = stringStateTransition.state
                    }

                    CodeState.DOC_STRING -> {
                        val docStringStateTransition = docStringState(
                            fileJob,
                            index,
                            endPoint,
                            endString,
                            currentState,
                            langFeatures.strings!!,
                        )
                        index = docStringStateTransition.index
                        currentState = docStringStateTransition.state
                    }

                    CodeState.MULTICOMMENT, CodeState.MULTICOMMENT_CODE -> {
                        val commentStateTransition = commentState(
                            fileJob,
                            index,
                            endPoint,
                            currentState,
                            endComments,
                            endString,
                            langFeatures
                        )
                        index = commentStateTransition.index
                        currentState = commentStateTransition.state
                        endString = commentStateTransition.endString
                        endComments = commentStateTransition.endComments
                    }

                    CodeState.BLANK, CodeState.MULTICOMMENT_BLANK -> {
                        val blankStateTransition = blankState(
                            fileJob,
                            index,
                            endPoint,
                            currentState,
                            endComments,
                            endString,
                            langFeatures
                        )
                        index = blankStateTransition.index
                        currentState = blankStateTransition.state
                        endString = blankStateTransition.endString
                        endComments = blankStateTransition.endComments
                        ignoreEscape = blankStateTransition.ignoreEscape
                    }

                    CodeState.COMMENT,
                    CodeState.COMMENT_CODE -> {

                    }
                }
            }

            // We shouldn't normally need this, but unclosed strings or comments
            // might leave the index past the end of the file when we reach this
            // point.
            if (index >= fileLen) return null

            // Only check the first 10000 characters for null bytes indicating a binary file
            // and if we find it then we return otherwise carry on and ignore binary markers
            if (index < 10000 && fileJob.binary) return null

            // This means the end of processing the line so calculate the stats according to what state
            // we are currently in
            if (fileJob.content[index] == '\n'.code.toByte() || index >= endPoint) {
                fileJob.lines++

                when (currentState) {
                    CodeState.CODE, CodeState.STRING, CodeState.COMMENT_CODE, CodeState.MULTICOMMENT_CODE -> {
                        fileJob.code++
                        currentState = resetState(currentState)
                    }

                    CodeState.COMMENT, CodeState.MULTICOMMENT, CodeState.MULTICOMMENT_BLANK -> {
                        fileJob.comment++
                        currentState = resetState(currentState)
                    }

                    CodeState.BLANK -> {
                        fileJob.blank++
                    }

                    CodeState.DOC_STRING -> {
                        fileJob.comment++
                    }
                }
            }

            index += 1
        }

        if (isDuplicates) {
            fileJob.hash.update(byteArrayOf())
        }

        return fileJob
    }

    private fun resetState(currentState: CodeState): CodeState {
        return when (currentState) {
            CodeState.MULTICOMMENT, CodeState.MULTICOMMENT_CODE -> CodeState.MULTICOMMENT
            CodeState.STRING -> CodeState.STRING
            else -> CodeState.BLANK
        }
    }

    private fun codeState(
        fileJob: FileJob,
        originIndex: Int,
        endPoint: Int,
        currentState: CodeState,
        endString: ByteArray,
        endComments: Array<ByteArray>,
        langFeatures: LanguageFeature,
        digest: MessageDigest
    ): CodeStateTransition {
        // Hacky fix to
        var point = endPoint
        if (point > fileJob.content.size) {
            point--
        }

        var id = originIndex;
        while (id in originIndex until point) {
            val curByte = fileJob.content[id]

            if (curByte == '\n'.code.toByte()) {
                return CodeStateTransition(id, currentState, endString, endComments, false)
            }

            if (LanguageService.isBinary(id, curByte)) {
                fileJob.binary = true
                return CodeStateTransition(id, currentState, endString, endComments, false)
            }

            if (shouldProcess(curByte, langFeatures.processMask!!)) {
                if (isDuplicates) {
                    // Technically this is wrong because we skip bytes so this is not a true
                    // hash of the file contents, but for duplicate files it shouldn't matter
                    // as both will skip the same way
                    val digestible = byteArrayOf(fileJob.content[id])
                    digest.update(digestible)
                }

                val rangeContent: ByteArray = fileJob.content.sliceArray(id until fileJob.content.size)
                val (tokenType, offsetJump, matchEndString) = langFeatures.tokens?.match(rangeContent)!!
                when (tokenType) {
                    TokenType.TString -> {
                        // If we are in string state then check what sort of string so we know if docstring OR ignoreescape string
                        val (i, ignoreEscape) = verifyIgnoreEscape(langFeatures, fileJob, id)

                        // It is safe to -1 here as to enter the code state we need to have
                        // transitioned from blank to here hence i should always be >= 1
                        // This check is to ensure we aren't in a character declaration
                        // TODO this should use language features
                        var state = currentState
                        if (fileJob.content[i - 1] != '\\'.code.toByte()) {
                            state = CodeState.STRING
                        }

                        return CodeStateTransition(i, state, matchEndString, endComments, ignoreEscape)
                    }

                    TokenType.TSlcomment -> {
                        return CodeStateTransition(id, CodeState.COMMENT_CODE, matchEndString, endComments, false)
                    }

                    TokenType.TMlcomment -> {
                        if (langFeatures.nested == true || endComments.isEmpty()) {
                            id += offsetJump - 1

                            return CodeStateTransition(
                                id,
                                CodeState.MULTICOMMENT_CODE,
                                matchEndString,
                                endComments.plus(matchEndString),
                                false
                            )
                        }
                    }

                    TokenType.TComplexity -> {
                        if (id == 0 || LanguageService.isWhitespace(fileJob.content[id - 1])) {
                            fileJob.complexity++
                        }
                    }

                    else -> {

                    }
                }
            }

            id += 1
        }

        return CodeStateTransition(id, currentState, endString, endComments, false)
    }

    private fun shouldProcess(currentByte: Byte, processBytesMask: Byte): Boolean {
        return currentByte and processBytesMask == currentByte
    }

    private fun commentState(
        fileJob: FileJob,
        originIndex: Int,
        endPoint: Int,
        currentState: CodeState,
        endComments: Array<ByteArray>,
        endString: ByteArray,
        langFeatures: LanguageFeature,
    ): CodeStateTransition {
        var state = currentState;

        var id = originIndex;
        while (id in originIndex until endPoint) {
            val curByte = fileJob.content[id]

            if (curByte == '\n'.code.toByte()) {
                return CodeStateTransition(index = id, state = state, endString = endString, endComments = endComments)
            }


            if (checkForMatchSingle(curByte, id, endPoint, endComments[endComments.size - 1], fileJob)) {
                val offsetJump = endComments[endComments.size - 1].size
                val newEndComments: Array<ByteArray> = endComments.sliceArray(0 until endComments.size - 1)

                if (newEndComments.isEmpty()) {
                    // If we started as multiline code switch back to code so we count correctly
                    // IE i := 1 /* for the lols */
                    // TODO is that required? Might still be required to count correctly
                    if (state == CodeState.MULTICOMMENT_CODE) {
                        state = CodeState.CODE // TODO pointless to change here, just set S_MULTICOMMENT_BLANK
                    } else {
                        state = CodeState.MULTICOMMENT_BLANK
                    }
                }

                id += offsetJump - 1
                return CodeStateTransition(
                    index = id,
                    state = state,
                    endString = endString,
                    endComments = newEndComments
                )
            }
            // Check if we are entering another multiline comment
            // This should come below check for match single as it speeds up processing
            if (langFeatures.nested == true || endComments.isEmpty()) {
                val rangeContent = fileJob.content.sliceArray(id until fileJob.content.size)
                val (ok, offsetJump, matchEndString) = langFeatures.multiLineComments?.match(rangeContent)!!
                if (ok != null && ok != TokenType.TString) {
                    id += offsetJump - 1

                    return CodeStateTransition(
                        index = id,
                        state = state,
                        endString = matchEndString,
                        endComments = endComments.plus(matchEndString),
                    )
                }
            }

            id += 1
        }

        return CodeStateTransition(
            index = id,
            state = state,
            endString = endString,
            endComments = endComments,
        )
    }

    private fun docStringState(
        fileJob: FileJob,
        originIndex: Int,
        endPoint: Int,
        endString: ByteArray,
        currentState: CodeState,
        stringTrie: Trie
    ): CodeStateTransition {
        // Its not possible to enter this state without checking at least 1 byte so it is safe to check -1 here
        // without checking if it is out of bounds first
        var id = originIndex
        for (i in originIndex until endPoint) {
            id = i

            if (fileJob.content[id] == '\n'.code.toByte()) {
                return CodeStateTransition(id, currentState)
            }

            if (fileJob.content[id - 1] != '\\'.code.toByte()) {
                val rangeContent: ByteArray = fileJob.content.sliceArray(id until endPoint)
                // So we have hit end of docstring at this point in which case check if only whitespace characters till the next
                // newline and if so we change to a comment otherwise to code
                // need to start the loop after ending definition of docstring, therefore adding the length of the string to
                // the index
                for (j in id + endString.size until endPoint) {
                    if (fileJob.content[j] == '\n'.code.toByte()) {
                        return CodeStateTransition(id, CodeState.COMMENT)
                    }

                    if (!LanguageService.isWhitespace(fileJob.content[j])) {
                        return CodeStateTransition(id, CodeState.CODE)
                    }
                }

                return CodeStateTransition(id, CodeState.CODE)
            }
        }

        return CodeStateTransition(id, currentState)
    }

    private fun stringState(
        fileJob: FileJob,
        index: Int,
        endPoint: Int,
        endString: ByteArray,
        currentState: CodeState,
        ignoreEscape: Boolean
    ): CodeStateTransition {
        var id = index
        // It's not possible to enter this state without checking at least 1 byte so it is safe to check -1 here
        // without checking if it is out of bounds first
        for (i in index..endPoint) {
            id = i

            // If we hit a newline, return because we want to count the stats but keep
            // the current state, so we end up back in this loop when the outer
            // one calls again
            if (fileJob.content[id] == '\n'.code.toByte()) {
                return CodeStateTransition(id, currentState)
            }

            // If we are in a literal string we want to ignore the \ check OR we aren't checking for special ones
            if (ignoreEscape || fileJob.content[id - 1] != '\\'.code.toByte()) {
                if (checkForMatchSingle(fileJob.content[i], id, endPoint, endString, fileJob)) {
                    return CodeStateTransition(id, CodeState.CODE)
                }
            }
        }

        return CodeStateTransition(index = id, state = currentState)
    }

    private fun blankState(
        fileJob: FileJob,
        index: Int,
        endPoint: Int,
        currentState: CodeState,
        endComments: Array<ByteArray>,
        endString: ByteArray,
        langFeatures: LanguageFeature
    ): CodeStateTransition {
        var state = currentState
        val rangeContent: ByteArray = fileJob.content.sliceArray(index until fileJob.content.size)
        val (tokenType, offsetJump, matchEndString) = langFeatures.tokens?.match(rangeContent)!!

        when (tokenType) {
            TokenType.TMlcomment -> {
                if (langFeatures.nested == true || endComments.isEmpty()) {
                    val newIndex = index + offsetJump - 1
                    return CodeStateTransition(
                        newIndex,
                        CodeState.MULTICOMMENT,
                        matchEndString,
                        endComments.plus(matchEndString),
                        false
                    )
                }
            }

            TokenType.TSlcomment -> {
                return CodeStateTransition(index, CodeState.COMMENT, matchEndString, endComments, false)
            }

            TokenType.TString -> {
                val (id, ignoreEscape) = verifyIgnoreEscape(langFeatures, fileJob, index)

                langFeatures.quotes?.forEach { v ->
                    if (v.end == matchEndString.toString() && v.docString) {
                        return CodeStateTransition(
                            id,
                            CodeState.DOC_STRING,
                            matchEndString,
                            endComments,
                            ignoreEscape
                        )
                    }
                }

                return CodeStateTransition(id, CodeState.STRING, matchEndString, endComments, ignoreEscape)
            }

            TokenType.TComplexity -> {
                state = CodeState.CODE
                if (index == 0 || LanguageService.isWhitespace(fileJob.content[index - 1])) {
                    fileJob.complexity++
                }
            }

            else -> {
                state = CodeState.CODE
            }
        }

        return CodeStateTransition(index, state, endString, endComments, false)
    }

    private fun verifyIgnoreEscape(langFeatures: LanguageFeature, fileJob: FileJob, index: Int): Pair<Int, Boolean> {
        var ignoreEscape = false

        for (i in 0 until langFeatures.quotes!!.size) {
            if (langFeatures.quotes[i].docString || langFeatures.quotes[i].ignoreEscape) {
                var isMatch = true
                for (j in 0 until langFeatures.quotes[i].start.length) {
                    if (fileJob.content.size <= index + j || fileJob.content[index + j] != langFeatures.quotes[i].start[j].code.toByte()) {
                        isMatch = false
                        break
                    }
                }

                if (isMatch) {
                    ignoreEscape = true
                    index + langFeatures.quotes[i].start.length
                }
            }
        }

        return Pair(index, ignoreEscape)
    }

    companion object {
        fun checkBomSkip(fileJob: FileJob): Int {
            // UTF-8 BOM which if detected we should skip the BOM as we can then count correctly
            // []byte is UTF-8 BOM taken from https://en.wikipedia.org/wiki/Byte_order_mark#Byte_order_marks_by_encoding
            if (fileJob.content.contentEquals(byteArrayOf(239.toByte(), 187.toByte(), 191.toByte()))) {
                return 3
            }

            // If we have one of the other BOM then we might not be able to count correctly so if verbose let the user know
            for (v in byteOrderMarks) {
                if (fileJob.content.contentEquals(v)) {
                    println("BOM found for file ${fileJob.filename} indicating it is not ASCII/UTF-8 and may be counted incorrectly or ignored as a binary file")
                }
            }

            return 0
        }

        fun checkForMatchSingle(
            currentByte: Byte,
            index: Int,
            endPoint: Int,
            matches: ByteArray,
            fileJob: FileJob
        ): Boolean {
            var potentialMatch = true

            if (currentByte == matches[0]) {
                for (j in matches.indices) {
                    if (index + j >= endPoint || matches[j] != fileJob.content[index + j]) {
                        potentialMatch = false
                        break
                    }
                }

                if (potentialMatch) {
                    return true
                }
            }

            return false
        }

        fun createFileJob(file: File): FileJob {
            val fileContent = file.readBytes()

            return FileJob(
                content = fileContent,
                filename = file.name,
                bytes = file.length(),
                extension = file.extension,
                location = file.absolutePath,
            )
        }
    }
}