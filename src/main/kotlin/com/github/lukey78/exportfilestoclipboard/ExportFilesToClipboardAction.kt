package com.github.lukey78.exportfilestoclipboard

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.StringSelection

class ExportFilesToClipboardAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val directory = event.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = event.project
        if (directory == null || project == null || !directory.isDirectory) return

        val projectBasePath = project.basePath ?: return
        val result = StringBuilder()
        val (totalFileCount, sizeInBytes) = countFiles(directory, projectBasePath)
        val sizeInKB = sizeInBytes/ 1024.0

        if (totalFileCount > 100 || sizeInKB > 1000) {
            val userChoice = Messages.showYesNoDialog(
                "Warning: You are exporting more than 100 files or more than 1000 kB of data. This may impact performance. Please try to only export a subtree.\n\n" +
                        "Total files: $totalFileCount\n" +
                        "Total size: %.0f KB".format(sizeInKB),
                "Warning: Many files",
                "Continue",
                "Cancel",
                Messages.getWarningIcon()
            )

            if (userChoice != Messages.YES) {
                return;
            }
        }



        val (exportedFileCount, exportedSizeInBytes) = traverseAndExportFileContent(directory, projectBasePath, result)

        // Copy to clipboard
        CopyPasteManager.getInstance().setContents(StringSelection(result.toString()))

        // Show success dialog
        val exportedSizeInKB = exportedSizeInBytes/ 1024.0
        Messages.showInfoMessage(
            "Content was successfully exported to the clipboard.\n\n" +
                    "Total files: ${exportedFileCount}\n" +
                    "Total size: %.0f KB".format(exportedSizeInKB),
            "\nExport successful"
        )
    }

    override fun update(event: AnActionEvent) {
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE)
        // Enable action only for directories
        event.presentation.isEnabledAndVisible = file != null && file.isDirectory
    }

    private fun countFiles(
        directory: VirtualFile,
        projectBasePath: String
    ): Pair<Int, Long> {
        var totalSize: Long = 0
        var totalCount: Int = 0
        for (file in directory.children) {
            if (file.isDirectory) {
                val (fileCount, filesInDirSize) = countFiles(file, projectBasePath) // Recursive call
                totalCount += fileCount
                totalSize += filesInDirSize
            } else if (!file.isBinary) {
                totalSize += file.length
                totalCount++
            }
        }
        return Pair(totalCount, totalSize)
    }

    private fun traverseAndExportFileContent(
        directory: VirtualFile,
        projectBasePath: String,
        result: StringBuilder
    ): Pair<Int, Long> {
        var totalSize: Long = 0
        var totalCount: Int = 0
        for (file in directory.children) {
            if (file.isDirectory) {
                val (fileCount, filesInDirSize) = traverseAndExportFileContent(file, projectBasePath, result) // Recursive call
                totalCount += fileCount
                totalSize += filesInDirSize
            } else if (!file.isBinary) {
                totalSize += appendFileContent(file, projectBasePath, result)
                totalCount++
            }
        }
        return Pair(totalCount, totalSize)
    }

    private fun appendFileContent(file: VirtualFile, projectBasePath: String, result: StringBuilder): Long {
        return try {
            val relativePath = file.path.removePrefix("$projectBasePath/")
            val content = String(file.contentsToByteArray())
            result.append("File: ").append(relativePath).append("\n")
            result.append(content).append("\n\n")
            content.toByteArray().size.toLong()
        } catch (e: Exception) {
            result.append("Error reading file: ").append(file.name).append("\n\n")
            0
        }
    }

    private val VirtualFile.isBinary: Boolean
        get() {
            return try {
                val content = contentsToByteArray()
                content.any { it < 0x09 || (it > 0x0D && it < 0x20) }
            } catch (e: Exception) {
                true // Treat unreadable files as binary
            }
        }
}