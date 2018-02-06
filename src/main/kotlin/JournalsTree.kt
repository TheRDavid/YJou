package yjouk

import javafx.scene.control.TextField
import javafx.scene.control.TreeCell
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeView
import javafx.scene.input.KeyCode
import javafx.util.Callback
import kutils.ui.fx.expandAll
import java.io.File

class JournalsTree(private val rootDirectory: JournalFile, val mainWindow: MainWindow) : TreeView<JournalFile>() {

    init {
        isEditable = true
        cellFactory = Callback<TreeView<JournalFile>, TreeCell<JournalFile>> {
            JournalTreeCell()
        }

        root = TreeItem<JournalFile>(JournalFile(rootDirectory.absolutePath))
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
                val oldCSSFile = JournalFile("${item.absolutePath}.css")
                val newFile = JournalFile("${item.parentFile.absolutePath}${File.separatorChar}${textField.text}")
                item.renameTo(newFile)
                oldCSSFile.renameTo(File("${newFile.absolutePath}.css"))
                item = newFile
                treeItem.value = item
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