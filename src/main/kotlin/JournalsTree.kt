package yjouk

import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.util.Callback
import kutils.ui.fx.expandAll
import java.awt.Desktop
import java.io.File

class JournalsTree(private val rootDirectory: JournalFile, val mainWindow: MainWindow) : TreeView<JournalFile>() {


    private val addMenuItems = arrayOf(
            object : MenuItem("Duplicate") {
                override fun fire() {
                    super.fire()
                    mainWindow.dataHandler.duplicate(selectionModel.selectedItem.value)
                }
            },
            object : MenuItem("Rename") {
                override fun fire() {
                    super.fire()
                    mainWindow.dataHandler.rename(selectionModel.selectedItem.value)
                }
            },
            object : MenuItem("Show in File Explorer") {
                override fun fire() {
                    super.fire()
                    Desktop.getDesktop().open(selectionModel.selectedItem.value.parentFile)
                }
            },
            object : MenuItem("Delete") {
                override fun fire() {
                    super.fire()
                    if (selectionModel.selectedItem.value.absolutePath == mainWindow.dataHandler.currentJournal?.absolutePath && mainWindow.dataHandler.delete(selectionModel.selectedItem.value))
                        mainWindow.loadStartPage()
                    update()
                }
            }
    )
    private val fileContextMenu = ContextMenu(*addMenuItems)

    init {
        isEditable = true
        cellFactory = Callback<TreeView<JournalFile>, TreeCell<JournalFile>> {
            JournalTreeCell()
        }

        root = TreeItem<JournalFile>(JournalFile(rootDirectory.absolutePath))
        setOnContextMenuRequested {
            fileContextMenu.show(this, it.screenX, it.screenY)
        }
        setOnKeyReleased {
            if (it.isControlDown) {
                when (it.code) {
                    KeyCode.E -> Desktop.getDesktop().open(selectionModel.selectedItem.value.parentFile)
                    KeyCode.C -> mainWindow.dataHandler.duplicate(selectionModel.selectedItem.value)
                    KeyCode.R -> mainWindow.dataHandler.rename(selectionModel.selectedItem.value)
                }
            } else if (it.code == KeyCode.DELETE) {
                if (selectionModel.selectedItem.value.absolutePath == mainWindow.dataHandler.currentJournal?.absolutePath && mainWindow.dataHandler.delete(selectionModel.selectedItem.value))
                    mainWindow.loadStartPage()
                update()
            }
        }
        selectionModel.selectedItemProperty().addListener { _, _, newValue ->
            if (newValue != null) mainWindow.loadJournal(newValue.value)
        }
        update()
    }

    fun update() {
        root.children.clear()
        update(rootDirectory, root)
        expandAll()
    }

    private fun update(directory: JournalFile, parentItem: TreeItem<JournalFile>) {
        directory.listFiles().filter { !it.name.startsWith(".") && !it.absolutePath.endsWith(".css") }.forEach {
            val journalFile = JournalFile(it.absolutePath)
            val item = TreeItem<JournalFile>(journalFile)
            parentItem.children.add(item)
            if (it.isDirectory) {
                update(journalFile, item)
            }
        }
    }

}

class JournalTreeCell : TreeCell<JournalFile>() {

    private val textField = TextField()

    init {
        textField.setOnKeyPressed { event ->
            if (event.code == KeyCode.ESCAPE)
                cancelEdit()
            else if (event.code == KeyCode.ENTER) {
                item = (treeView as JournalsTree).mainWindow.dataHandler.rename(item, textField.text)
                cancelEdit()
                updateItem(item, false)
                (treeView as JournalsTree).update()
            }
        }
    }

    override fun startEdit() {
        super.startEdit()
        graphic = textField
        text = ""
        textField.selectAll()
        textField.requestFocus()
    }

    override fun cancelEdit() {
        super.cancelEdit()
        graphic = treeItem.graphic
        text = item.toString()
    }

    override fun updateItem(item: JournalFile?, empty: Boolean) {
        super.updateItem(item, empty)
        if (empty) {
            text = null
            graphic = null
        } else {
            if (isEditing) {
                textField.text = item?.toString() ?: ""
                text = null
                graphic = textField
            } else {
                text = item?.toString() ?: ""
                graphic = treeItem.graphic
            }
        }
    }

}


class JournalFile(pathname: String?) : File(pathname) {
    override fun toString(): String {
        return absoluteFile.name
    }
}