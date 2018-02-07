package yjouk

import javafx.application.Platform
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.control.ChoiceDialog
import javafx.scene.control.TextInputDialog
import org.w3c.dom.Node
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintStream
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.text.SimpleDateFormat
import java.util.*
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

data class DataHandler(val rootDir: File,
                       private val archiveDir: File = File("${rootDir.absolutePath}${File.separatorChar}$archiveDirName"),
                       private val defaultTemplateJournal: File = File("${rootDir.absolutePath}${File.separatorChar}.templates${File.separatorChar}defaultTemplate"),
                       val defaultTemplateJournalStyle: File = File("${rootDir.absolutePath}${File.separatorChar}.templates${File.separatorChar}${defaultTemplateJournal.name}.css")) {
    companion object {
        const val archiveDirName = ".archive"
        val assetsDir = File("src${File.separatorChar}assets${File.separatorChar}appIcon.png")
        val remoteDefaultTemplateJournal = URL("https://raw.githubusercontent.com/TheRDavid/YJou/master/mybrainspace/.templates/defaultTemplate")
        val remoteDefaultTemplateJournalStyle = URL("https://raw.githubusercontent.com/TheRDavid/YJou/master/mybrainspace/.templates/defaultTemplate.css")
    }

    var currentJournal: JournalFile? = null
    val startPageURL = "file:///${File("." +
            "${File.separatorChar}src" +
            "${File.separatorChar}assets" +
            "${File.separatorChar}startPage" +
            "${File.separatorChar}index.html").absolutePath}"
    private val ignoreFileChanges = mutableListOf<Pair<File, File?>>()

    private enum class Placeholder {
        JOURNAL_NAME
    }

    init {
        if (!rootDir.exists()) {
            rootDir.mkdir()
        }
        if (!archiveDir.exists()) {
            archiveDir.mkdir()
        }
    }

    fun saveCurrentJournal(node: Node) {
        currentJournal?.let {
            val transformer = TransformerFactory.newInstance().newTransformer()

            transformer.transform(DOMSource(node),
                    StreamResult(OutputStreamWriter(PrintStream(currentJournal), "UTF-8")))
        }
    }

    fun addDefaultJournal(path: String) {
        checkTemplates()

        val journalName = path.substring(path.lastIndexOf(File.separatorChar) + 1)
        var defJournal = defaultTemplateJournal.readText()
        defJournal = defJournal.replacePlaceHolder(Placeholder.JOURNAL_NAME.toString(), journalName)

        defaultTemplateJournalStyle.copyTo(File("$path.css"))
        File(path).printWriter().use { it.print(defJournal) }
    }

    fun checkTemplates() {
        try {
            if (!defaultTemplateJournal.exists()) {
                defaultTemplateJournal.parentFile.mkdirs()
                defaultTemplateJournal.writeText(DataHandler.remoteDefaultTemplateJournal.readText())
            }
            if (!defaultTemplateJournalStyle.exists()) {
                defaultTemplateJournalStyle.parentFile.mkdirs()
                defaultTemplateJournalStyle.writeText(DataHandler.remoteDefaultTemplateJournalStyle.readText())
            }
        } catch (e: Exception) {
            val alert = Alert(Alert.AlertType.ERROR, "Great, your template files are missing / incomplete and I can't download them because there's no bloody connection! So all your newly created files will just be blank...\nNext time you're connected, delete your new templates so the program will attempt to get them online again.")
            alert.title = "Could not find templates"
            createOfflineTemplates()
        }
    }

    private fun createOfflineTemplates() {
        defaultTemplateJournal.parentFile.mkdirs()
        if (!defaultTemplateJournal.exists())
            defaultTemplateJournal.printWriter().use {
                it.print(
                        "<HTML>\n" +
                                "    <HEAD>\n" +
                                "        <link rel=\"stylesheet\" type=\"text/css\" href=\"[[[JOURNAL_NAME]]].css\">\n" +
                                "    </HEAD>\n" +
                                "    <BODY>\n" +
                                "        <DIV class=\"defaultText\" contenteditable=\"true\">herp merp derp</DIV></BODY>" +
                                "</HTML>")
            }
        if (!defaultTemplateJournalStyle.exists())
            defaultTemplateJournalStyle.createNewFile()
    }

    private fun String.replacePlaceHolder(placeholderName: String, value: String): String {
        return replace("[[[$placeholderName]]]", value)
    }

    fun firstJournal(): File {
        return rootDir.listFiles()[0]
    }

    fun registerRenaming(from: File, to: File) {
        ignoreFileChanges.add(Pair(from, to))
    }

    fun registerWatcher(watcherUI: MainWindow) = Thread(Runnable {

        val path = FileSystems.getDefault().getPath(rootDir.absolutePath)
        val watchService = FileSystems.getDefault().newWatchService()
        path.register(watchService, arrayOf(StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY))

        while (watcherUI.running) {
            Thread.sleep(1000)
            val key = watchService.take()

            var updateTree = false
            var goToStartingPage = false
            var updateArea = false
            var saveCurrentFile = false

            key.pollEvents().forEach { e ->
                when (e.kind()) {
                    StandardWatchEventKinds.ENTRY_CREATE -> {
                        updateTree = true
                        Platform.runLater({
                            watcherUI.update(updateTree, updateArea, goToStartingPage, saveCurrentFile)
                        })
                    }
                    StandardWatchEventKinds.ENTRY_DELETE -> {
                        updateTree = true
                        if (consumeRenameChanges(e) && currentJournal?.absolutePath == rootDir.resolve((e.context() as Path).toFile()).absolutePath) {
                            Platform.runLater({
                                val choices = listOf("Recreate", "Close", "Close and delete stylesheet")
                                val choiceDialog = ChoiceDialog<String>(choices[0], choices)
                                choiceDialog.title = "Whops"
                                choiceDialog.headerText = "Some dofus deleted this file(${currentJournal?.name})."
                                if (choiceDialog.showAndWait().isPresent) {
                                    when (choiceDialog.selectedItem) {
                                        choices[0] -> {
                                            saveCurrentFile = true
                                            currentJournal?.createNewFile()
                                        }
                                        choices[1] -> goToStartingPage = true
                                        choices[2] -> {
                                            goToStartingPage = true; File(currentJournal?.absolutePath + ".css").delete()
                                        }
                                    }
                                }
                                watcherUI.update(true, updateArea, goToStartingPage, saveCurrentFile)
                            })
                        }
                    }
                    StandardWatchEventKinds.ENTRY_MODIFY -> {
                        updateArea = updateArea || FileSystems.getDefault().getPath(watcherUI.currentJournal().absolutePath) == e.context() as Path
                        Platform.runLater({
                            watcherUI.update(updateTree, updateArea, goToStartingPage, saveCurrentFile)
                        })
                    }
                }
            }
            key.reset()
        }
    }).start()

    private fun consumeRenameChanges(event: WatchEvent<*>): Boolean {
        ignoreFileChanges.forEach {
            println("Checking ${it.first.name} -> ${it.second?.name} against ${(event.context() as Path).toFile().absolutePath}")
            if (!it.first.exists()
                    && currentJournal?.exists() == false
                    && rootDir.resolve((event.context() as Path).toFile()).absolutePath == currentJournal?.absolutePath) {
                println("Ignoring renaming of ${it.first.name}")
                ignoreFileChanges.remove(it)
                return false
            }
        }
        return true
    }

    private fun archive(file: File) {
        if (file.name != archiveDirName) {
            if (!archiveDir.exists()) archiveDir.mkdir()
            file.copyRecursively(File(archiveDir.absolutePath +
                    "${File.separatorChar}" +
                    SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(Date(System.currentTimeMillis())) +
                    "_${file.name}"))
        }
    }

    fun delete(file: JournalFile): Boolean {
        Alert(Alert.AlertType.CONFIRMATION, "You sure about deleting ${file.name}${if (file.isDirectory) "and all contained files" else ""}?", ButtonType.YES, ButtonType.CANCEL).showAndWait()?.let {
            if (it.get() == ButtonType.YES) {
                archive(file as File)
                ignoreFileChanges.add(Pair(file, null))
                file.deleteRecursively()
                System.gc()                     // unblock file... yup
                File("${file.absolutePath}.css").delete()
                return true
            }
        }
        return false
    }

    fun duplicate(file: JournalFile) {
        val nameDialog = TextInputDialog(file.name)
        nameDialog.title = "Duplicate File"
        nameDialog.headerText = "Copy Name:"
        val input = nameDialog.showAndWait()
        if (input.isPresent) {
            file.copyRecursively(File("${file.parentFile.absolutePath}${File.separator}i${input.get()}"))
        }
    }

    fun rename(item: JournalFile) {
        val inputDialog = TextInputDialog("derp")
        inputDialog.title = "Rename"
        inputDialog.headerText = "Set new name:"
        val input = inputDialog.showAndWait()
        if (input.isPresent)
            rename(item, input.get())
    }

    fun rename(item: JournalFile, text: String?): JournalFile {
        val oldCSSFile = JournalFile("${item.absolutePath}.css")
        val newFile = JournalFile("${item.parentFile.absolutePath}${File.separatorChar}$text")
        registerRenaming(item.absoluteFile, newFile)
        item.renameTo(newFile)
        oldCSSFile.renameTo(File("${newFile.absolutePath}.css"))
        return newFile
    }

}