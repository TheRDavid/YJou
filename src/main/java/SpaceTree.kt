import javafx.scene.control.TextField
import javafx.scene.control.TreeCell
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeView
import javafx.scene.input.KeyCode
import javafx.util.Callback
import kutils.expandAll
import java.io.File

class SpaceTree(private val rootDirectory: SpaceFile, val mainWindow: MainWindow) : TreeView<SpaceFile>() {

    private val forbiddenNames = arrayOf("defaultJournal")

    init {
        isEditable = true
        cellFactory = Callback<TreeView<SpaceFile>, TreeCell<SpaceFile>> {
            SpaceTreeCell()
        }

        root = TreeItem<SpaceFile>(SpaceFile(rootDirectory.absolutePath))
        selectionModel.selectedItemProperty().addListener { _, _, newValue ->
            if (newValue != null) mainWindow.loadSpace(newValue.value)
        }
        update()
    }

    fun update() {
        root.children.clear()
        update(rootDirectory, root)
        expandAll()
    }

    private fun update(directory: SpaceFile, parentItem: TreeItem<SpaceFile>) {
        directory.listFiles().filter { !(forbiddenNames.contains(it.name) || it.name.startsWith(".") || it.absolutePath.endsWith(".css")) }.forEach {
            val spaceFile = SpaceFile(it.absolutePath)
            val item = TreeItem<SpaceFile>(spaceFile)
            parentItem.children.add(item)
            if (it.isDirectory) {
                update(spaceFile, item)
            }
        }
    }

}

class SpaceTreeCell : TreeCell<SpaceFile>() {

    private val textField = TextField()

    init {
        textField.setOnKeyPressed { event ->
            if (event.code == KeyCode.ESCAPE)
                cancelEdit()
            else if (event.code == KeyCode.ENTER) {
                val newFile = SpaceFile("${item.parentFile.absolutePath}${File.separatorChar}${textField.text}")
                item.renameTo(newFile)
                item = newFile
                treeItem.value = item
                cancelEdit()
                updateItem(item, false)
                (treeView as SpaceTree).update()
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

    override fun updateItem(item: SpaceFile?, empty: Boolean) {
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


class SpaceFile(pathname: String?) : File(pathname) {
    override fun toString(): String {
        return absoluteFile.name
    }
}