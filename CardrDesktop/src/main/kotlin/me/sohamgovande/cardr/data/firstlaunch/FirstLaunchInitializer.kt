package me.sohamgovande.cardr.data.firstlaunch
import me.sohamgovande.cardr.data.prefs.Prefs
import me.sohamgovande.cardr.data.prefs.PrefsObject
import me.sohamgovande.cardr.data.urls.UrlHelper
import me.sohamgovande.cardr.util.*
import net.lingala.zip4j.ZipFile
import org.apache.commons.io.IOUtils
import org.apache.logging.log4j.LogManager
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Paths

private val logger = LogManager.getLogger(Prefs::class.java)

@Throws(FirstLaunchException::class, Exception::class)
private fun downloadChromeDataWindows(): File {
    executeCommandBlocking("taskkill /f /im CardifyChromeApp.exe", logger, true)
    executeCommandBlocking("taskkill /f /im CardrChromeApp.exe", logger, true)

    val executable = Paths.get(System.getProperty("cardr.data.dir"), "CardrChromeApp.exe").toFile()
    downloadFileFromURL(UrlHelper.get("winChromeApp"), executable, logger)

    val jsonFile = Paths.get(System.getProperty("cardr.data.dir"), "me.sohamgovande.cardr.json").toFile()
    downloadFileFromURL(UrlHelper.get("winChromeJson"), jsonFile, logger)

    if (!jsonFile.exists())
        throw FirstLaunchException("Unable to download Chrome App Native JSON.")

    return jsonFile
}

@Throws(FirstLaunchException::class, Exception::class)
private fun downloadChromeDataMacOS() {
    logger.info("Entered macOS First Launch")
    logger.info("Creating paths...")
    val jsonPath = Paths.get(System.getProperty("user.home"), "Library", "Application Support", "Google", "Chrome", "NativeMessagingHosts", "me.sohamgovande.cardr.json")
    if (!jsonPath.parent.parent.toFile().exists())
        throw FirstLaunchException("No Google Chrome installation detected")

    val executablePath = Paths.get(System.getProperty("cardr.data.dir"), "CardrChromeApp")
    val executableZipPath = Paths.get(System.getProperty("cardr.data.dir"), "CardrChromeApp.zip")
    try { Files.createDirectories(executablePath.parent) } catch (e: FileAlreadyExistsException) { }

    logger.info("Opening data stream for json file...")
    val dataStream = UrlHelper.url("macChromeJson").openStream()

    logger.info("Reading json file...")
    @Suppress("DEPRECATION") var data = IOUtils.toString(dataStream)
    IOUtils.closeQuietly(dataStream)

    logger.info("Writing json file to ${jsonPath.toFile().absolutePath} with executable ${executablePath.toFile().absolutePath}...")
    data = data.replace("%FILEPATH%", executablePath.toFile().absolutePath)
    Files.write(jsonPath, data.toByteArray())

    downloadFileFromURL(UrlHelper.get("macChromeApp"), executableZipPath.toFile(), logger)

    logger.info("Extracting zipped executable...")
    val zipFile = ZipFile(executableZipPath.toFile())
    zipFile.extractAll(executablePath.parent.toFile().absolutePath)
    executableZipPath.toFile().deleteOnExit()

    makeFileExecutableViaChmod(executablePath.toFile().absolutePath, logger)

    if (!jsonPath.toFile().exists())
        throw FirstLaunchException("Unable to download Chrome App Native JSON.")
    if (!executablePath.toFile().exists())
        throw FirstLaunchException("Unable to download Chrome App Native Executable.")
}

@Throws(FirstLaunchException::class, Exception::class)
private fun onFirstLaunchWindows() {
    val jsonFile = downloadChromeDataWindows()

    val commands = arrayOf(
        "REG DELETE \"HKCU\\Software\\Google\\Chrome\\NativeMessagingHosts\\me.sohamgovande.cardr\" /f",
        "REG DELETE \"HKLM\\Software\\Google\\Chrome\\NativeMessagingHosts\\me.sohamgovande.cardr\" /f",
        "REG ADD \"HKCU\\Software\\Google\\Chrome\\NativeMessagingHosts\\me.sohamgovande.cardr\" /ve /t REG_SZ /d \"${jsonFile.absolutePath}\" /f"
    )
    for (cmd in commands) {
        executeCommandBlocking(cmd, logger, true)
    }
}

@Throws(FirstLaunchException::class, Exception::class)
private fun onFirstLaunchMacOS() {
    downloadChromeDataMacOS()

    val macScriptsPath = Paths.get(System.getProperty("cardr.data.dir"), "MacScripts")
    try { Files.createDirectory(macScriptsPath) } catch (e: FileAlreadyExistsException) { }

    val getWordWindowsScriptPath = Paths.get(macScriptsPath.toFile().absolutePath, "getWordWindows.scpt")
    val selectWordWindowScriptPath = Paths.get(macScriptsPath.toFile().absolutePath, "selectWordWindow.scpt")

    downloadFileFromURL(UrlHelper.get("getWordWindows"), getWordWindowsScriptPath.toFile(), logger)
    downloadFileFromURL(UrlHelper.get("selectWordWindow"), selectWordWindowScriptPath.toFile(), logger)

    if (!selectWordWindowScriptPath.toFile().exists())
        throw FirstLaunchException("Unable to download AppleScript 'selectWordWindow'.")
    if (!getWordWindowsScriptPath.toFile().exists())
        throw FirstLaunchException("Unable to download AppleScript 'getWordWindows'.")
}

fun onFirstLaunch(): Exception? {
    return try {
        logger.info("First launch method invoked with OS ${System.getProperty("os.name")}")
        if (getOSType() == OS.WINDOWS)
            onFirstLaunchWindows()
        if (getOSType() == OS.MAC)
            onFirstLaunchMacOS()
        null
    } catch (e: Exception) {
        e
    }
}

fun updateFrom(from: Int, to: Int): Exception? {
    if (from == 1 && to >= 2) {
        logger.info("Resetting card format...")
        val prefs = Prefs.get()
        prefs.cardFormat = PrefsObject.DEFAULT_CARD_FORMAT
        if (getOSType() == OS.MAC) {
            prefs.cardFormat = prefs.cardFormat.replace("Calibri", PrefsObject.MAC_CALIBRI_FONT)
        }
    }
    if (from < 3 && to >= 3) {
        logger.info("Updating CardrChromeApp")
        if (getOSType() == OS.MAC) {
            downloadChromeDataMacOS()
        } else {
            downloadChromeDataWindows()
        }
    }
    return null
}
