/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright © 2023 Strato Team and Contributors (https://github.com/strato-emu/)
 */

package emu.skyline.preference

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.Preference.SummaryProvider
import com.google.android.material.snackbar.Snackbar
import emu.skyline.R
import emu.skyline.fragments.IndeterminateProgressDialogFragment
import emu.skyline.getPublicFilesDir
import emu.skyline.settings.SettingsActivity
import emu.skyline.utils.ZipUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilenameFilter
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.experimental.xor

class FirmwareImportPreference @JvmOverloads constructor(context : Context, attrs : AttributeSet? = null, defStyleAttr : Int = androidx.preference.R.attr.preferenceStyle) : Preference(context, attrs, defStyleAttr) {
    private class Firmware(val valid : Boolean, val version : String)

    private val firmwarePath = File(context.getPublicFilesDir().canonicalPath + "/switch/nand/system/Contents/registered/")
    private val keysPath = "${context.filesDir.canonicalPath}/keys/"
    private val fontsPath = "${context.getPublicFilesDir().canonicalPath}/fonts/"
    private val avatarPath = "${context.getPublicFilesDir().canonicalPath}/avatar/"

    private val documentPicker = (context as ComponentActivity).registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri ->
            val inputZip = context.contentResolver.openInputStream(uri)
            if (inputZip == null) {
                Snackbar.make((context as SettingsActivity).binding.root, R.string.error, Snackbar.LENGTH_LONG).show()
                return@registerForActivityResult
            }

            val cacheFirmwareDir = File("${context.cacheDir.path}/registered/")

            val task : () -> Unit = {
                var messageToShow : Int

                try {
                    // Unzip in cache dir to not delete previous firmware in case the zip given doesn't contain a valid one
                    ZipUtils.unzip(inputZip, cacheFirmwareDir)

                    val firmware = isFirmwareValid(cacheFirmwareDir)
                    messageToShow = if (!firmware.valid) {
                        R.string.import_firmware_invalid_contents
                    } else {
                        firmwarePath.deleteRecursively()
                        cacheFirmwareDir.copyRecursively(firmwarePath, true)
                        persistString(firmware.version)
                        extractFonts(firmwarePath.path, keysPath, fontsPath)
                        runBlocking { extractAvatarImage() }
                        CoroutineScope(Dispatchers.Main).launch {
                            notifyChanged()
                        }
                        R.string.import_firmware_success
                    }
                } catch (e : IOException) {
                    messageToShow = R.string.error
                } finally {
                    cacheFirmwareDir.deleteRecursively()
                }

                Snackbar.make((context as SettingsActivity).binding.root, messageToShow, Snackbar.LENGTH_LONG).show()
            }

            IndeterminateProgressDialogFragment.newInstance(context as SettingsActivity, R.string.import_firmware_in_progress, task)
                .show(context.supportFragmentManager, IndeterminateProgressDialogFragment.TAG)
        }
    }

    init {
        val keysDir = File(keysPath)
        isEnabled = keysDir.exists() && keysDir.listFiles()?.isNotEmpty() == true

        summaryProvider = SummaryProvider<FirmwareImportPreference> { preference ->
            val defaultString = if (preference.isEnabled)
                context.getString(R.string.firmware_not_installed)
            else
                context.getString(R.string.firmware_keys_needed)

            getPersistedString(defaultString)
        }
    }

    //override fun onClick() = documentPicker.launch(arrayOf("application/zip"))
    override fun onClick() = runBlocking { extractAvatarImage() }

    /**
     * Checks if the given directory stores a valid firmware. For that, all files must be NCAs and
     * one of them must store the firmware version.
     * @return A pair that tells if the firmware is valid, and if so, which firmware version it is
     */
    private fun isFirmwareValid(cacheFirmwareDir : File) : Firmware {
        val filterNCA = FilenameFilter { _, dirName -> dirName.endsWith(".nca") }

        val unfilteredNumOfFiles = cacheFirmwareDir.list()?.size ?: -1
        val filteredNumOfFiles = cacheFirmwareDir.list(filterNCA)?.size ?: -2

        return if (unfilteredNumOfFiles == filteredNumOfFiles) {
            val version = fetchFirmwareVersion(cacheFirmwareDir.path, keysPath)
            Firmware(version.isNotEmpty(), version)
        } else Firmware(false, "")
    }

    private suspend fun extractAvatarImage() {
        //File(avatarPath).mkdir()
        //extractAvatarImage(firmwarePath.path, keysPath, avatarPath)

        // Convert decoded .szs files to .png
        val jobs = ArrayList<Job>()
        File(avatarPath).walkTopDown().forEach { file ->
            if (file.extension != "szs")
                return@forEach

            jobs.add(CoroutineScope(Dispatchers.IO).launch {
                val decompressedData = decompressYaz0(file.readBytes()) ?: return@launch
                val input = DataInputStream(ByteArrayInputStream(decompressedData))

                val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888, true)
                val pixels = IntArray(256 * 256)
                for (i in 0 until 256 * 256) {
                    val rgba = input.readInt()
                    val rgb = rgba ushr 8
                    val a = rgba and 0xFF
                    pixels[i] = (a shl 24) or rgb
                }
                bitmap.setPixels(pixels, 0, 256, 0, 0, 256, 256)

                val png = File(file.canonicalPath.replace(".szs", ".png"))
                png.delete()
                png.createNewFile()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, png.outputStream())
            })

            /*DataInputStream(FileInputStream(file)).use { input ->
                val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888, true)
                val pixels = IntArray(256 * 256)
                for (i in 0 until 256 * 256) {
                    val rgba = input.readInt()
                    val rgb = rgba ushr 8
                    val a = rgba and 0xFF
                    pixels[i] = (a shl 24) or rgb
                }
                bitmap.setPixels(pixels, 0, 256, 0, 0, 256, 256)

                val png = File(file.canonicalPath.replace(".szs", ".png"))
                png.delete()
                png.createNewFile()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, png.outputStream())
                file.delete()
            }*/
        }

        jobs.forEach { it.join() }
    }

    private fun decompressYaz0(compressedYaz0FileData : ByteArray) : ByteArray? {
        val compressedFileData = ByteBuffer.wrap(compressedYaz0FileData)

        val magic = ByteArray(4)
        compressedFileData.get(magic)
        if (String(magic) != "Yaz0")
            return null

        // Size in bytes of the decompressed data
        val decompressedSize = compressedFileData.getInt()

        // Data starts at offset 0x10
        compressedFileData.position(0x10)

        val decompressedData = ByteArray(decompressedSize)

        var dstPos = 0

        var groupHeader = 0
        var groupHeaderLength = 0

        while (compressedFileData.hasRemaining() && dstPos < decompressedSize) {
            if (groupHeaderLength == 0) {
                groupHeader = compressedFileData.get().toInt() and 0xFF
                groupHeaderLength = 8
            }
            groupHeaderLength--

            if (groupHeader and 0x80 != 0) {
                // A set bit (=1) in the group header means, that the chunk is exact 1 byte long. This byte must be copied to the output stream 1:1.
                decompressedData[dstPos++] = compressedFileData.get()
            } else {
                // A cleared bit (=0) defines, that the chunk is 2 or 3 bytes long interpreted as a backreference to already decompressed data that must be copied.
                val byte1 = compressedFileData.get().toInt() and 0xFF
                val byte2 = compressedFileData.get().toInt() and 0xFF

                val dist = ((byte1 and 0x0F) shl 8) or byte2
                var copySource = dstPos - (dist + 1)
                var numBytes = byte1 ushr 4
                if (numBytes == 0)
                    numBytes = (compressedFileData.get().toInt() and 0xFF) + 0x12
                else
                    numBytes += 2

                repeat(numBytes) {
                    decompressedData[dstPos++] = decompressedData[copySource++]
                }
            }
            groupHeader = groupHeader shl 1
        }

        return decompressedData
    }

    private external fun fetchFirmwareVersion(systemArchivesPath : String, keysPath : String) : String
    private external fun extractFonts(systemArchivesPath : String, keysPath : String, fontsPath : String)
    private external fun extractAvatarImage(systemArchivesPath : String, keysPath : String, avatarPath : String)
}
