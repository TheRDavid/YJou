import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.web.WebView
import javafx.stage.Stage
import kutils.io.deleteRecursively
import kutils.ui.fx.firstItemByValue
import java.io.File

class MainWindow : Application() {

    private val dataHandler = DataHandler(File("mybrainspace"))
    private val contentsTree = JournalsTree(JournalFile(dataHandler.rootDir.absolutePath), this)
    private val addFileButton = Button("+")
    private val deleteFileButton = Button("-")
    private val fileControls = HBox(addFileButton, deleteFileButton)
    private val contentArea = WebView()
    private val leftPanel = BorderPane()
    private val mainPane = SplitPane(leftPanel, contentArea)
    private var selectedJournal = dataHandler.firstJournal()
    private var lastSaveTimestamp: Long = 0
    private val saveInterval: Long = 500
    private val startPageURL = "file:///${File("." +
            "${File.separatorChar}src" +
            "${File.separatorChar}assets" +
            "${File.separatorChar}startPage" +
            "${File.separatorChar}index.html").absolutePath}"

    var running: Boolean = true

    private fun build(): Parent {
        contentsTree.selectionModel.selectedItemProperty().addListener { _, oldValue, newValue ->
            println((
                    if (oldValue == null) "first selection -> ${newValue.value.name}"
                    else "Selection went from ${oldValue.value.name} to ${newValue.value.name}"))
        }
        contentArea.setOnKeyReleased {
            if (System.currentTimeMillis() - lastSaveTimestamp > saveInterval) {
                dataHandler.saveCurrentJournal(contentArea.engine.document.getElementsByTagName("HTML").item(0))
                lastSaveTimestamp = System.currentTimeMillis()
            }
        }
        fileControls.alignment = Pos.BOTTOM_RIGHT
        addFileButton.setPrefSize(30.0, 30.0)
        addFileButton.setOnMouseClicked {
            val inputDialog = TextInputDialog("There and Back Again")
            inputDialog.title = "Create a new Journal"
            inputDialog.headerText = "Set Journal name"
            val input = inputDialog.showAndWait()
            if (input.isPresent) {
                val parentItem = contentsTree.selectionModel.selectedItem ?: contentsTree.root
                val newFile = JournalFile("" +
                        (if (parentItem.value.isDirectory) parentItem else parentItem.parent).value.absolutePath +
                        "${File.separatorChar}" +
                        input.get())
                if (newFile.exists()) Alert(Alert.AlertType.ERROR, "Journal already exists m8", ButtonType.OK)
                else {
                    dataHandler.addDefaultJournal(newFile.absolutePath)
                    contentsTree.update()
                    contentsTree.selectionModel.select(contentsTree.firstItemByValue(newFile))
                }
            }
        }
        deleteFileButton.setPrefSize(30.0, 30.0)
        deleteFileButton.setOnMouseClicked {
            val selectedItem = contentsTree.selectionModel.selectedItem
            selectedItem?.let {
                Alert(Alert.AlertType.CONFIRMATION, "You sure abount deleting ${selectedItem.value.name}${if (selectedItem.value.isDirectory) "and all contained files" else ""}?", ButtonType.YES, ButtonType.CANCEL).showAndWait()?.let {
                    if (it.get() == ButtonType.YES) {
                        if (selectedItem.value == dataHandler.currentJournal) {
                            contentArea.engine.load(startPageURL)
                        }
                        dataHandler.currentJournal = JournalFile("")
                        dataHandler.archive(selectedItem.value)
                        selectedItem.value.deleteRecursively()
                        System.gc()                     // unblock file... yup
                        File("${selectedItem.value.absolutePath}.css").delete()
                        contentsTree.update()
                    }
                }
            }
        }
        leftPanel.bottom = fileControls
        leftPanel.center = contentsTree
        contentArea.engine.load(startPageURL)

        mainPane.setDividerPosition(0, 0.3)
        update(true, false, false, false)
        return mainPane
    }

    fun loadJournal(journal: JournalFile) {
        dataHandler.currentJournal?.let {
            if (it.exists()) {
                val source = contentArea.engine.document.getElementsByTagName("HTML").item(0)
                dataHandler.saveCurrentJournal(source)
                System.gc()                     // unblock file... yup
            }
        }
        if (!journal.isDirectory) {
            contentArea.engine.loadContent(journal.readText())
            val styleFile = File("${journal.parentFile.absolutePath}${File.separatorChar}${journal.name}.css")
            if (!styleFile.exists()) {
                dataHandler.checkTemplates()
                styleFile.writeText(dataHandler.defaultTemplateJournalStyle.readText())
            }
            contentArea.engine.userStyleSheetLocation = "file:///$styleFile"
            dataHandler.currentJournal = journal
        }
    }

    override fun start(primaryStage: Stage) {
        primaryStage.setOnCloseRequest {
            dataHandler.saveCurrentJournal(contentArea.engine.document.getElementsByTagName("HTML").item(0))
        }
        primaryStage.scene = Scene(build(), 1280.0, 720.0)
        primaryStage.title = dataHandler.rootDir.name
        primaryStage.icons.add(Image("file:${DataHandler.assetsDir}"))
        primaryStage.show()
        dataHandler.registerWatcher(this@MainWindow)
    }

    override fun stop() {
        super.stop()
        System.exit(0)
    }

    fun update(updateTree: Boolean, updateEditArea: Boolean, goToStartingPage: Boolean, saveCurrentFile: Boolean) {
        if (updateTree) {
            contentsTree.update()
        }
        if (saveCurrentFile) {
            dataHandler.saveCurrentJournal(contentArea.engine.document.getElementsByTagName("HTML").item(0))
        }
        if (goToStartingPage) {
            Platform.runLater({ contentArea.engine.load(startPageURL) })
        }
    }

    fun currentJournal(): File {
        return selectedJournal
    }

}

fun main(args: Array<String>) {
    Application.launch(MainWindow::class.java, *args)
}
