import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.text.SimpleDateFormat
import java.util.*

data class DataHandler(val rootDir: File,
                       private val archiveDir: File = File("${rootDir.absolutePath}${File.separatorChar}$archiveDirName"),
                       private val defaultJournal: File = File("${rootDir.absolutePath}${File.separatorChar}.templates${File.separatorChar}defaultTemplate"),
                       private val defaultStyle: File = File("${rootDir.absolutePath}${File.separatorChar}.templates${File.separatorChar}${defaultJournal.name}.css")) {
    companion object {
        const val archiveDirName = ".archive"
        val assetsDir = File("src${File.separatorChar}assets${File.separatorChar}appIcon.png")
    }

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

    fun addDefaultJournal(path: String) {
        val journalName = path.substring(path.lastIndexOf(File.separatorChar) + 1)
        var defJournal = defaultJournal.readText()

        defJournal = defJournal.replacePlaceHolder(Placeholder.JOURNAL_NAME.toString(), journalName)

        defaultStyle.copyTo(File("$path.css"))
        File(path).printWriter().use { it.print(defJournal) }
    }

    private fun String.replacePlaceHolder(placeholderName: String, value: String): String {
        return replace("[[[$placeholderName]]]", value)
    }

    fun firstJournal(): File {
        return rootDir.listFiles()[0]
    }

    fun registerWatcher(watcherUI: MainWindow) {
        Thread(Runnable {

            val path = FileSystems.getDefault().getPath(rootDir.absolutePath)
            val watchService = FileSystems.getDefault().newWatchService()
            path.register(watchService, arrayOf(StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY))

            while (watcherUI.running) {
                Thread.sleep(1000)
                val key = watchService.take()

                var updateTree = false
                var closeArea = false
                var updateArea = false

                key.pollEvents().forEach { e ->
                    when (e.kind()) {
                        StandardWatchEventKinds.ENTRY_CREATE -> {
                            updateTree = true
                        }
                        StandardWatchEventKinds.ENTRY_DELETE -> {
                            updateTree = true
                            closeArea = closeArea || FileSystems.getDefault().getPath(watcherUI.currentJournal().absolutePath) == e.context() as Path
                        }
                        StandardWatchEventKinds.ENTRY_MODIFY ->
                            updateArea = updateArea || FileSystems.getDefault().getPath(watcherUI.currentJournal().absolutePath) == e.context() as Path
                    }
                }
                watcherUI.update(updateTree, closeArea, updateArea)
                key.reset()
            }
        }).start()
    }

    fun archive(file: File) {
        if (!archiveDir.exists()) archiveDir.mkdir()
        file.copyRecursively(File(archiveDir.absolutePath +
                "${File.separatorChar}" +
                SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(Date(System.currentTimeMillis())) +
                "_${file.name}"))
    }

}