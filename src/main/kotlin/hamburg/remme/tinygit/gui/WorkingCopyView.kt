package hamburg.remme.tinygit.gui

import hamburg.remme.tinygit.I18N
import hamburg.remme.tinygit.TinyGit
import hamburg.remme.tinygit.domain.File
import hamburg.remme.tinygit.gui.builder.Action
import hamburg.remme.tinygit.gui.builder.ActionGroup
import hamburg.remme.tinygit.gui.builder.addClass
import hamburg.remme.tinygit.gui.builder.confirmWarningAlert
import hamburg.remme.tinygit.gui.builder.contextMenu
import hamburg.remme.tinygit.gui.builder.errorAlert
import hamburg.remme.tinygit.gui.builder.splitPane
import hamburg.remme.tinygit.gui.builder.stackPane
import hamburg.remme.tinygit.gui.builder.toolBar
import hamburg.remme.tinygit.gui.builder.vbox
import hamburg.remme.tinygit.gui.builder.vgrow
import hamburg.remme.tinygit.gui.builder.visibleWhen
import hamburg.remme.tinygit.gui.component.Icons
import hamburg.remme.tinygit.shortName
import javafx.beans.binding.Bindings
import javafx.collections.ListChangeListener
import javafx.scene.control.MultipleSelectionModel
import javafx.scene.control.SelectionMode
import javafx.scene.control.Tab
import javafx.scene.input.KeyCode
import javafx.scene.layout.Priority
import javafx.scene.text.Text
import java.util.concurrent.Callable

class WorkingCopyView : Tab() {

    private val service = TinyGit.workingCopyService
    private val state = TinyGit.state
    private val window get() = content.scene.window

    val actions get() = arrayOf(ActionGroup(updateAll, stageAll, stageSelected), ActionGroup(unstageAll, unstageSelected))
    private val unstageAll = Action(I18N["workingCopy.unstageAll"], { Icons.arrowAltCircleDown() }, "Shortcut+Shift+L", state.canUnstageAll.not(),
            { service.unstage() })
    private val unstageSelected = Action(I18N["workingCopy.unstageSelected"], { Icons.arrowAltCircleDown() }, disable = state.canUnstageSelected.not(),
            handler = { unstageSelected() })
    private val updateAll = Action(I18N["workingCopy.updateAll"], { Icons.arrowAltCircleUp() }, disable = state.canUpdateAll.not(),
            handler = { service.update() })
    private val stageAll = Action(I18N["workingCopy.stageAll"], { Icons.arrowAltCircleUp() }, "Shortcut+Shift+K", state.canStageAll.not(),
            { service.stage() })
    private val stageSelected = Action(I18N["workingCopy.stageSelected"], { Icons.arrowAltCircleUp() }, disable = state.canStageSelected.not(),
            handler = { stageSelected() })

    private val staged = FileStatusView(service.staged, SelectionMode.MULTIPLE).vgrow(Priority.ALWAYS)
    private val pending = FileStatusView(service.pending, SelectionMode.MULTIPLE).vgrow(Priority.ALWAYS)
    private val selectedStaged = staged.selectionModel
    private val selectedPending = pending.selectionModel

    init {
        text = I18N["workingCopy.tab"]
        graphic = Icons.hdd()
        isClosable = false

        val unstageKey = KeyCode.L
        val stageKey = KeyCode.K
        val deleteKey = KeyCode.DELETE
        val discardKey = KeyCode.D

        val unstageFile = Action("${I18N["workingCopy.unstage"]} (${unstageKey.shortName})", { Icons.arrowAltCircleDown() }, disable = state.canUnstageSelected.not(),
                handler = { unstageSelected() })

        staged.contextMenu = contextMenu {
            isAutoHide = true
            +ActionGroup(unstageFile)
        }
        staged.setOnKeyPressed {
            if (!it.isShortcutDown) when (it.code) {
                unstageKey -> if (state.canUnstageSelected.get()) unstageSelected()
                else -> Unit
            }
        }

        // TODO: menubar actions?
        val stageFile = Action("${I18N["workingCopy.stage"]} (${stageKey.shortName})", { Icons.arrowAltCircleUp() }, disable = state.canStageSelected.not(),
                handler = { stageSelected() })
        val deleteFile = Action("${I18N["workingCopy.delete"]} (${deleteKey.shortName})", { Icons.trash() }, disable = state.canDeleteSelected.not(),
                handler = { deleteFile() })
        val discardChanges = Action("${I18N["workingCopy.discard"]} (${discardKey.shortName})", { Icons.undo() }, disable = state.canDiscardSelected.not(),
                handler = { discardChanges() })

        pending.contextMenu = contextMenu {
            isAutoHide = true
            +ActionGroup(stageFile)
            +ActionGroup(deleteFile, discardChanges)
        }
        pending.setOnKeyPressed {
            if (!it.isShortcutDown) when (it.code) {
                stageKey -> if (state.canStageSelected.get()) stageSelected()
                deleteKey -> if (state.canDeleteSelected.get()) deleteFile()
                discardKey -> if (state.canDiscardSelected.get()) discardChanges()
                else -> Unit
            }
        }

        selectedStaged.selectedItems.addListener(ListChangeListener { service.selectedStaged.setAll(it.list) })
        selectedStaged.selectedItemProperty().addListener({ _, _, it -> it?.let { selectedPending.clearSelection() } })

        selectedPending.selectedItems.addListener(ListChangeListener { service.selectedPending.setAll(it.list) })
        selectedPending.selectedItemProperty().addListener({ _, _, it -> it?.let { selectedStaged.clearSelection() } })

        val fileDiff = FileDiffView(Bindings.createObjectBinding(
                Callable { selectedStaged.selectedItem ?: selectedPending.selectedItem },
                selectedStaged.selectedItemProperty(), selectedPending.selectedItemProperty()))

        content = stackPane {
            +splitPane {
                addClass("working-copy-view")

                +splitPane {
                    addClass("files")

                    +vbox {
                        +toolBar {
                            +StatusCountView(staged.items)
                            addSpacer()
                            +unstageAll
                            +unstageSelected
                        }
                        +staged
                    }
                    +vbox {
                        +toolBar {
                            +StatusCountView(pending.items)
                            addSpacer()
                            +updateAll
                            +stageAll
                            +stageSelected
                        }
                        +pending
                    }
                }
                +fileDiff
            }
            +stackPane {
                addClass("overlay")
                visibleWhen(Bindings.isEmpty(staged.items).and(Bindings.isEmpty(pending.items)))
                +Text(I18N["workingCopy.nothingToCommit"])
            }
        }

        TinyGit.addListener { fileDiff.refresh() }
    }

    private fun stageSelected() {
        val selected = getIndex(selectedPending)
        service.stageSelected { setIndex(selectedPending, selected) }
    }

    private fun unstageSelected() {
        val selected = getIndex(selectedStaged)
        service.unstageSelected { setIndex(selectedStaged, selected) }
    }

    private fun deleteFile() {
        if (!confirmWarningAlert(window, I18N["dialog.deleteFiles.header"], I18N["dialog.deleteFiles.button"],
                        I18N["dialog.deleteFiles.text", I18N["workingCopy.selectedFiles", selectedPending.selectedItems.size]])) return
        val selected = getIndex(selectedPending)
        service.delete { setIndex(selectedPending, selected) }
    }

    private fun discardChanges() {
        if (!confirmWarningAlert(window, I18N["dialog.discardChanges.header"], I18N["dialog.discardChanges.button"],
                        I18N["dialog.discardChanges.text", I18N["workingCopy.selectedFiles", selectedPending.selectedItems.size]])) return
        val selected = getIndex(selectedPending)
        service.discardChanges(
                { setIndex(selectedPending, selected) },
                { errorAlert(window, I18N["dialog.cannotDiscard.header"], it) })
    }

    private fun getIndex(selectionModel: MultipleSelectionModel<File>): Int {
        return if (selectionModel.selectedItems.size == 1) selectionModel.selectedIndex else -1
    }

    private fun setIndex(selectionModel: MultipleSelectionModel<File>, index: Int) {
        selectionModel.clearAndSelect(index)
        selectionModel.selectedItem ?: selectionModel.selectLast()
    }

}
