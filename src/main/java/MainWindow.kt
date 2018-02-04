import javafx.application.Application
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.web.WebView
import javafx.stage.Stage
import kutils.firstItemByValue
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintStream
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult


class MainWindow : Application() {

    private val dataHandler = DataHandler(File("mybrainspace"))
    private val contentsTree = SpaceTree(SpaceFile(dataHandler.rootDir.absolutePath), this)
    private val addFileButton = Button("+")
    private val deleteFileButton = Button("-")
    private val fileControls = HBox(addFileButton, deleteFileButton)
    private val contentArea = WebView()
    private val leftPanel = BorderPane()
    private val mainPane = SplitPane(leftPanel, contentArea)
    private var selectedJournal = dataHandler.firstJournal()
    private var lastSaveTimestamp: Long = 0
    private val saveInterval: Long = 500
    private lateinit var currentSpace: SpaceFile

    var running: Boolean = true

    private fun build(): Parent {
        contentArea.setOnKeyReleased {
            if (System.currentTimeMillis() - lastSaveTimestamp > saveInterval) {
                val doc = contentArea.engine.document
                val transformer = TransformerFactory.newInstance().newTransformer()
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
                transformer.setOutputProperty(OutputKeys.METHOD, "xml")
                transformer.setOutputProperty(OutputKeys.INDENT, "yes")
                transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")

                transformer.transform(DOMSource(doc),
                        StreamResult(OutputStreamWriter(PrintStream(currentSpace), "UTF-8")))
                lastSaveTimestamp = System.currentTimeMillis()
            }
        }
        fileControls.alignment = Pos.BOTTOM_RIGHT
        addFileButton.setPrefSize(30.0, 30.0)
        addFileButton.setOnMouseClicked {
            val input = TextInputDialog().showAndWait()
            if (input.isPresent) {
                val parentItem = contentsTree.selectionModel.selectedItem ?: contentsTree.root
                val newFile = SpaceFile("" +
                        (if (parentItem.value.isDirectory) parentItem else parentItem.parent).value.absolutePath +
                        "${File.separatorChar}" +
                        input.get())
                if (newFile.exists()) Alert(Alert.AlertType.ERROR, "Journal already exists m8", ButtonType.OK)
                else {
                    dataHandler.addDefaultSpace(newFile.absolutePath)
                    contentsTree.update()
                    contentsTree.selectionModel.select(contentsTree.firstItemByValue(newFile))
                }
            }
        }
        deleteFileButton.setPrefSize(30.0, 30.0)
        deleteFileButton.setOnMouseClicked {
            val selectedItem = contentsTree.selectionModel.selectedItem
            selectedItem?.let {
                Alert(Alert.AlertType.CONFIRMATION, "Really?", ButtonType.YES, ButtonType.CANCEL).showAndWait()?.let {
                    if (it.get() == ButtonType.YES) {
                        dataHandler.archive(selectedItem.value)
                        selectedItem.value.delete()
                        contentsTree.update()
                    }
                }
            }
        }
        leftPanel.bottom = fileControls
        leftPanel.center = contentsTree
        mainPane.setDividerPosition(0, 0.3)
        update(true, false, true)
        return mainPane
    }

    fun loadSpace(space: SpaceFile) {
        if (!space.isDirectory) {
            contentArea.engine.loadContent("<div contenteditable=\"true\">${space.readText()}</div>")
            println("file:///${space.parentFile.absolutePath}${File.separatorChar}${space.nameWithoutExtension}.css")

            contentArea.engine.userStyleSheetLocation = "file:///${space.parentFile.absolutePath}${File.separatorChar}${space.nameWithoutExtension}.css"
            currentSpace = space
        }
    }

    override fun start(primaryStage: Stage) {
        primaryStage.scene = Scene(build(), 1280.0, 720.0)
        primaryStage.title = dataHandler.rootDir.name
        primaryStage.show()
        dataHandler.registerWatcher(this@MainWindow)
    }

    override fun stop() {
        super.stop()
        System.exit(0)
    }

    fun update(updateTree: Boolean, closeArea: Boolean, updateEditArea: Boolean) {
        if (updateTree) {
            contentsTree.update()
        }
    }

    fun currentJournal(): File {
        return selectedJournal
    }

}

fun main(args: Array<String>) {
    Application.launch(MainWindow::class.java, *args)
}
