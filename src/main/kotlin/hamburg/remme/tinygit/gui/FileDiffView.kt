package hamburg.remme.tinygit.gui

import hamburg.remme.tinygit.git.LocalCommit
import hamburg.remme.tinygit.git.LocalFile
import hamburg.remme.tinygit.git.LocalRepository
import hamburg.remme.tinygit.git.api.Git
import hamburg.remme.tinygit.gui.builder.VBoxBuilder
import hamburg.remme.tinygit.gui.builder.comboBox
import hamburg.remme.tinygit.gui.builder.toolBar
import hamburg.remme.tinygit.gui.builder.vgrow
import hamburg.remme.tinygit.gui.builder.webView
import hamburg.remme.tinygit.htmlEncodeAll
import javafx.scene.control.ComboBox
import javafx.scene.control.ListCell
import javafx.scene.layout.Priority
import javafx.scene.web.WebEngine
import javafx.util.Callback

class FileDiffView : VBoxBuilder() {

    private val contextLines: ComboBox<Int>
    private val fileDiff: WebEngine
    private val emptyDiff = /*language=HTML*/ """
        <tr>
            <td class="line-number header">&nbsp;</td>
            <td class="line-number header">&nbsp;</td>
            <td class="code header">&nbsp;@@ No changes detected or binary file @@</td>
        </tr>
    """
    private var repository: LocalRepository? = null
    private var commit: LocalCommit? = null
    private var file: LocalFile? = null

    init {
        contextLines = comboBox {
            items.setAll(0, 1, 3, 6, 12, 25, 50, 100)
            buttonCell = ContextLinesListCell()
            cellFactory = Callback { ContextLinesListCell() }
            value = 3
            valueProperty().addListener { _, _, it ->
                if (commit == null) setContent(Git.diff(repository!!, file!!, it))
                else setContent(Git.diff(repository!!, file!!, commit!!, it))
            }
        }
        +toolBar {
            addSpacer()
            +contextLines
        }

        val webView = webView {
            vgrow(Priority.ALWAYS)
            isContextMenuEnabled = false
            prefWidth = 400.0
            prefHeight = 300.0
        }
        fileDiff = webView.engine
        +webView
        clearContent()
    }

    fun update(newRepository: LocalRepository, newFile: LocalFile) {
        if (newRepository != repository || newFile != file) {
            repository = newRepository
            file = newFile
            commit = null

            setContent(Git.diff(newRepository, newFile, contextLines.value))
        }
    }

    fun update(newRepository: LocalRepository, newFile: LocalFile, newCommit: LocalCommit) {
        if (newRepository != repository || newFile != file || newCommit != commit) {
            repository = newRepository
            file = newFile
            commit = newCommit

            setContent(Git.diff(newRepository, newFile, newCommit, contextLines.value))
        }
    }

    fun clearContent() {
        repository = null
        file = null
        commit = null

        //language=HTML
        fileDiff.loadContent("""
            <html>
            <head>
                <style>
                    html, body {
                        background-color: #3c3f41;
                    }
                </style>
            </head>
            </html>
        """)
    }

    // TODO: slow and can make the ui stuck on huge files, e.g. package-lock.json
    private fun setContent(diff: String) {
        //language=HTML
        fileDiff.loadContent("""
            <html>
            <head>
                <style>
                    html, body {
                        padding: 0;
                        margin: 0;
                        font: 12px "Liberation Mono", monospace;
                        color: #ccc;
                        background-color: #3c3f41;
                    }
                    hr {
                        height: 1px;
                        background-color: #aaa;
                        border: none;
                    }
                    table {
                        position: absolute;
                        min-width: 100%;
                        font-size: 13px;
                    }
                    .line-number {
                        padding: 3px 6px;
                        text-align: right;
                        color: rgba(255,255,255,0.6);
                        background-color: #535759;
                    }
                    .line-number.header {
                        padding: 6px 0;
                        background-color: #4e6e80;
                    }
                    .line-number.added {
                        background-color: #4e8054;
                    }
                    .line-number.removed {
                        background-color: #804e4e;
                    }
                    .line-number.conflict {
                        background-color: #80804e;
                    }
                    .code {
                        width: 100%;
                        white-space: nowrap;
                    }
                    .code.header {
                        color: #aaa;
                        background-color: #354b57;
                    }
                    .code.added {
                        background-color: #36593b;
                    }
                    .code.removed {
                        background-color: #593636;
                    }
                    .code.conflict {
                        background-color: #575735;
                    }
                    .code.eof {
                        color: rgba(255,255,255,0.6);
                    }
                    .marker {
                        -webkit-user-select: none;
                        user-select: none;
                        margin-left: 4px;
                        padding: 0 2px;
                        color: rgba(255,255,255,0.45);
                        background-color: rgba(255,255,255,0.15);
                        border-radius: 2px;
                        font-size: 11px;
                    }
                </style>
            </head>
            <body>
                <table cellpadding="0" cellspacing="0">
                    ${format(diff)}
                </table>
            </body>
            </html>
        """)
    }

    private fun format(diff: String): String {
        if (diff.isBlank() || diff.matches(".*Binary files differ\\r?\\n?$".toRegex(RegexOption.DOT_MATCHES_ALL))) return emptyDiff

        val blocks = diff.lines()
                .filter { it.startsWith('@') }
                .map {
                    val match = ".*?(\\d+)(,\\d+)?.*?(\\d+)(,\\d+)?.*".toRegex().matchEntire(it)!!.groups
                    DiffBlock(
                            match[1]?.value?.toInt() ?: 1,
                            match[2]?.value?.substring(1)?.toInt() ?: 1,
                            match[3]?.value?.toInt() ?: 1,
                            match[4]?.value?.substring(1)?.toInt() ?: 1)
                }
        var blockIndex = -1

        val lineNumbers = LineNumbers()
        var conflict = false
        // TODO: still buggy for conflicts
        return diff.lines()
                .dropLast(1)
                .map {
                    if (it.startsWith('@')) {
                        blockIndex++
                        lineNumbers.left = blocks[blockIndex].number1
                        lineNumbers.right = blocks[blockIndex].number2
                    }
                    if (blockIndex >= 0) {
                        if (it.startsWith("+<<<<<<<")) conflict = true
                        val line = formatLine(it, lineNumbers, blocks[blockIndex], conflict)
                        if (it.startsWith("+>>>>>>>")) conflict = false
                        return@map line
                    }
                    return@map ""
                }
                .joinToString("")
                .takeIf { it.isNotBlank() }
                ?: emptyDiff
    }

    private fun formatLine(line: String, numbers: LineNumbers, block: DiffBlock, conflict: Boolean): String {
        val codeClass: String
        val oldLineNumber: String
        val newLineNumber: String
        when {
        // TODO: don't show empty line diffs
            line.startsWith('@') -> return /*language=HTML*/ """
                <tr>
                    <td class="line-number header">&nbsp;</td>
                    <td class="line-number header">&nbsp;</td>
                    <td class="code header">&nbsp;@@ -${block.number1},${block.length1} +${block.number2},${block.length2} @@</td>
                </tr>
            """
            line.startsWith("+++") -> return ""
            line.startsWith("---") -> return ""
            line.startsWith('+') -> {
                newLineNumber = "${numbers.right++}"
                oldLineNumber = "&nbsp;"
                codeClass = if (conflict) "conflict" else "added"
            }
            line.startsWith('-') -> {
                newLineNumber = "&nbsp;"
                oldLineNumber = "${numbers.left++}"
                codeClass = if (conflict) "conflict" else "removed"
            }
            line.startsWith('\\') -> {
                newLineNumber = "&nbsp;"
                oldLineNumber = "&nbsp;"
                codeClass = if (conflict) "conflict" else "eof"
            }
            line.startsWith(' ') -> {
                oldLineNumber = "${numbers.left++}"
                newLineNumber = "${numbers.right++}"
                codeClass = if (conflict) "conflict" else "&nbsp;"
            }
            else -> return ""
        }
        return /*language=HTML*/ """
            <tr>
                <td class="line-number $codeClass">$oldLineNumber</td>
                <td class="line-number $codeClass">$newLineNumber</td>
                <td class="code $codeClass">${line.trimHash().htmlEncodeAll()}</td>
            </tr>
        """
    }

    private fun String.trimHash() = if (startsWith('#')) substring(1) else this

    private class LineNumbers(var left: Int = 0, var right: Int = 0)

    private class DiffBlock(val number1: Int, val length1: Int, val number2: Int, val length2: Int)

    private class ContextLinesListCell : ListCell<Int>() {
        override fun updateItem(item: Int?, empty: Boolean) {
            super.updateItem(item, empty)
            text = "$item lines"
        }
    }

}
