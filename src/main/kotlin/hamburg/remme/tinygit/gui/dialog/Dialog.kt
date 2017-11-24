package hamburg.remme.tinygit.gui.dialog

import hamburg.remme.tinygit.State
import hamburg.remme.tinygit.gui.builder.addClass
import hamburg.remme.tinygit.gui.builder.disabledWhen
import hamburg.remme.tinygit.gui.builder.image
import javafx.beans.value.ObservableBooleanValue
import javafx.scene.Node
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import javafx.scene.image.Image
import javafx.stage.Modality
import javafx.stage.Window
import javafx.util.Callback
import javafx.scene.control.Dialog as FXDialog

abstract class Dialog(window: Window, title: String, resizable: Boolean = false) {

    protected var okAction: () -> Unit = {}
    protected var cancelAction: () -> Unit = {}
    protected var focusAction: () -> Unit = {}
    private val dialog: FXDialog<Unit> = FXDialog()

    init {
        dialog.initModality(Modality.WINDOW_MODAL)
        dialog.initOwner(window)
        dialog.title = title
        dialog.isResizable = resizable
        dialog.resultConverter = Callback {
            when {
                it == null -> cancelAction.invoke()
                it.buttonData == ButtonBar.ButtonData.OK_DONE -> okAction.invoke()
                it.buttonData == ButtonBar.ButtonData.CANCEL_CLOSE -> cancelAction.invoke()
            }
        }
        dialog.dialogPane.scene.window.focusedProperty().addListener { _, _, it -> if (it) focusAction.invoke() }
    }

    fun show() {
        State.modalVisible.set(true)
        dialog.show()
    }

    protected fun setHeader(text: String) {
        dialog.dialogPane.headerText = text
    }

    protected fun setIcon(icon: Image) {
        dialog.graphic = image {
            addClass("icon")
            image = icon
            isSmooth = true
            fitWidth = 32.0
            fitHeight = 32.0
        }
    }

    protected fun setContent(content: Node) {
        dialog.dialogPane.content = content
    }

    protected fun setButton(vararg button: ButtonType) {
        dialog.dialogPane.buttonTypes.setAll(*button)
    }

    protected fun setButtonBinding(button: ButtonType, disable: ObservableBooleanValue) {
        dialog.dialogPane.lookupButton(button).disabledWhen(disable)
    }

}
