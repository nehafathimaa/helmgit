package hamburg.remme.tinygit.gui

import hamburg.remme.tinygit.State
import hamburg.remme.tinygit.git.LocalCommit
import hamburg.remme.tinygit.git.LocalGit
import hamburg.remme.tinygit.git.LocalRepository
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.property.ReadOnlyStringWrapper
import javafx.concurrent.Task
import javafx.scene.control.Label
import javafx.scene.control.ProgressIndicator
import javafx.scene.control.SplitPane
import javafx.scene.control.Tab
import javafx.scene.control.TableCell
import javafx.scene.control.TableView
import javafx.scene.layout.HBox
import javafx.scene.layout.StackPane
import javafx.util.Callback

class LogView : Tab() {

    private val pane = SplitPane()
    private val error = StackPane()
    private val overlay = StackPane(ProgressIndicator(-1.0))
    private val localCommits = TableView<LocalCommit>()
    private var logTask: Task<*>? = null

    init {
        text = "Log"
        graphic = FontAwesome.list()
        isClosable = false

        val message = tableColumn<LocalCommit, LocalCommit>("Message",
                cellValue = Callback { ReadOnlyObjectWrapper(it.value) },
                cellFactory = Callback { LogMessageTableCell() })
        val date = tableColumn<LocalCommit, String>("Date",
                cellValue = Callback { ReadOnlyStringWrapper(it.value.date.format(shortDate)) })
        val author = tableColumn<LocalCommit, String>("Author",
                cellValue = Callback { ReadOnlyStringWrapper(it.value.author) })
        val commit = tableColumn<LocalCommit, String>("Commit",
                cellValue = Callback { ReadOnlyStringWrapper(it.value.shortId) })

        localCommits.columns.addAll(message, date, author, commit)
        localCommits.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        localCommits.selectionModel.selectedItemProperty().addListener { _, _, it ->
            setCommit(State.getSelectedRepository(), it)
        }

        pane.styleClass += "log-view"
        pane.items += localCommits

        error.children += HBox(
                Label("Fetching repository failed. Check the settings. "),
                Label("", FontAwesome.cog()))
                .also { it.styleClass += "box" }
        error.styleClass += "overlay"
        error.isVisible = false

        overlay.styleClass += "progress-overlay"

        content = StackPane(pane, error, overlay)

        State.selectedRepositoryProperty().addListener { _, _, it -> fetchCommits(it) }
        State.addRefreshListener { fetchCurrent() }
    }

    private fun setCommit(repository: LocalRepository, commit: LocalCommit?) {
        var dividerPosition = 0.5
        if (pane.items.size > 1) {
            dividerPosition = pane.dividerPositions[0]
            pane.items.removeAt(1)
        }
        commit?.let {
            pane.items += CommitDetailsView(repository, it)
            pane.setDividerPosition(0, dividerPosition)
        }
    }

    private fun fetchCurrent() {
        if (State.hasSelectedRepository()) fetchCommits(State.getSelectedRepository())
    }

    private fun fetchCommits(repository: LocalRepository) {
        println("Fetching: $repository")
        logTask?.cancel()
        logTask = object : Task<List<LocalCommit>>() {
            val selected = localCommits.selectionModel.selectedItem

            override fun call(): List<LocalCommit> {
                return LocalGit.log(repository)
            }

            override fun succeeded() {
                overlay.isVisible = false
                localCommits.items.setAll(value)
                localCommits.items.find { it == selected }?.let { localCommits.selectionModel.select(it) }
                localCommits.selectionModel.selectedItem ?: localCommits.selectionModel.selectFirst()
            }

            override fun failed() {
                error.isVisible = true
                overlay.isVisible = false
                exception.printStackTrace()
            }
        }
        error.isVisible = false
        overlay.isVisible = true
        State.cachedThreadPool.execute(logTask)
    }

    private class LogMessageTableCell : TableCell<LocalCommit, LocalCommit>() {

        override fun updateItem(item: LocalCommit?, empty: Boolean) {
            super.updateItem(item, empty)
            text = item?.shortMessage
            graphic = if (empty) null else {
                if (item!!.branches.isNotEmpty()) {
                    HBox(4.0, *item.branches.map { BranchBadge(it.shortRef, it.current) }.toTypedArray())
                } else null
            }
        }

    }

    private class BranchBadge(name: String, current: Boolean) : Label(name, FontAwesome.codeFork()) {

        init {
            styleClass += "branch-badge"
            if (current) styleClass += "current"
        }

    }

}
