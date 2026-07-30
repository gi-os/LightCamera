package com.gios.lightcamera.roll

import android.content.Context
import android.util.Log
import com.gios.lightcamera.media.MediaStoreRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** One exposure on a loaded roll. Not in the gallery, not visible, not yet a photograph. */
data class Exposure(
    val index: Int,
    val file: File,
    val takenAt: Long,
    val filterId: String,
    val width: Int,
    val height: Int,
)

/** A roll of film, part way through. */
data class Roll(
    val number: Int,
    val length: Int,
    val startedAt: Long,
    val exposures: List<Exposure>,
) {
    val shot: Int get() = exposures.size
    val remaining: Int get() = (length - shot).coerceAtLeast(0)
    val finished: Boolean get() = shot >= length
}

/**
 * Film-roll mode.
 *
 * A loaded roll takes the review out of taking photographs. Exposures go into app-private
 * storage, not the gallery; the shutter gives you a counter and a haptic click and nothing
 * else; and the frames only become photographs — files in `DCIM/Camera`, visible to every
 * other app — when the roll is developed, either because it ran out or because you chose to.
 *
 * The point isn't nostalgia. It's that checking the screen after every shot changes what you
 * photograph, and a roll makes that impossible for twenty-four frames at a time.
 *
 * Storage is a directory of JPEGs plus a plain-text index, one line per frame. No database:
 * the whole state is a dozen lines that has to survive a crash mid-roll and be readable by
 * eye when something has gone wrong. Each frame is written before the index line, so a
 * process death between the two loses the index entry and leaves an orphan file, which
 * [recover] sweeps up — the other order would leave an index pointing at a file that isn't
 * there.
 */
class FilmRoll(private val context: Context) {

    private val prefs = context.getSharedPreferences("roll", Context.MODE_PRIVATE)

    private val _roll = MutableStateFlow<Roll?>(null)
    val roll: StateFlow<Roll?> = _roll.asStateFlow()

    /** How many rolls have been developed. Only used to number the next one. */
    val developedCount: Int get() = prefs.getInt(KEY_DEVELOPED, 0)

    init {
        _roll.value = readLoadedRoll()
    }

    private fun rollsDir(): File = File(context.filesDir, "rolls").apply { mkdirs() }

    private fun dirFor(number: Int): File = File(rollsDir(), "roll-$number").apply { mkdirs() }

    private fun indexFile(number: Int): File = File(dirFor(number), "index.txt")

    /* ---------------- loading and unloading ---------------- */

    fun load(length: Int) {
        if (_roll.value != null) return
        val number = developedCount + 1
        val roll = Roll(number = number, length = length, startedAt = System.currentTimeMillis(), exposures = emptyList())
        prefs.edit()
            .putInt(KEY_CURRENT, number)
            .putInt(KEY_LENGTH, length)
            .putLong(KEY_STARTED, roll.startedAt)
            .apply()
        dirFor(number)
        _roll.value = roll
    }

    /** Throw the roll away unexposed. Asked for twice in the UI, for obvious reasons. */
    fun discard() {
        val roll = _roll.value ?: return
        runCatching { dirFor(roll.number).deleteRecursively() }
        clearLoaded()
        _roll.value = null
    }

    private fun clearLoaded() {
        prefs.edit().remove(KEY_CURRENT).remove(KEY_LENGTH).remove(KEY_STARTED).apply()
    }

    /* ---------------- exposing ---------------- */

    /**
     * Commit a frame to the roll. Returns the roll as it now stands, or null if there was
     * no roll loaded — the caller uses that to decide whether to save to the gallery instead.
     */
    suspend fun expose(
        jpeg: ByteArray,
        takenAt: Long,
        filterId: String,
        width: Int,
        height: Int,
    ): Roll? = withContext(Dispatchers.IO) {
        val current = _roll.value ?: return@withContext null
        if (current.finished) return@withContext current
        val index = current.shot + 1
        val file = File(dirFor(current.number), frameName(index))
        val ok = runCatching { file.writeBytes(jpeg) }
            .onFailure { Log.e(TAG, "frame $index failed to write", it) }
            .isSuccess
        if (!ok) return@withContext current

        val exposure = Exposure(index, file, takenAt, filterId, width, height)
        runCatching {
            indexFile(current.number).appendText(
                listOf(index, takenAt, filterId, width, height, file.name).joinToString("|") + "\n",
            )
        }
        val updated = current.copy(exposures = current.exposures + exposure)
        _roll.value = updated
        updated
    }

    /* ---------------- developing ---------------- */

    /**
     * Write every frame into the camera roll, oldest first, and unload.
     *
     * Each frame keeps the time it was *taken*, so a roll shot over three weeks lands in the
     * gallery spread across three weeks rather than as a block of today. The files are only
     * removed once their `MediaStore` row exists, so a failure halfway leaves the rest of
     * the roll intact and developable again.
     */
    suspend fun develop(repo: MediaStoreRepo): DevelopedRoll = withContext(Dispatchers.IO) {
        val current = _roll.value ?: return@withContext DevelopedRoll(0, emptyList(), 0)
        var failed = 0
        val uris = ArrayList<android.net.Uri>(current.exposures.size)
        for (exposure in current.exposures.sortedBy { it.index }) {
            val bytes = runCatching { exposure.file.readBytes() }.getOrNull()
            if (bytes == null) {
                failed++
                continue
            }
            val uri = repo.save(
                jpeg = bytes,
                takenAt = exposure.takenAt,
                width = exposure.width,
                height = exposure.height,
                suffix = "R${current.number}F%02d".format(exposure.index),
            )
            if (uri == null) {
                failed++
            } else {
                uris += uri
                runCatching { exposure.file.delete() }
            }
        }
        if (failed == 0) {
            runCatching { dirFor(current.number).deleteRecursively() }
            prefs.edit().putInt(KEY_DEVELOPED, current.number).apply()
            clearLoaded()
            _roll.value = null
        } else {
            // Keep the roll loaded so the frames that survived can be tried again.
            _roll.value = current.copy(
                exposures = current.exposures.filter { it.file.exists() },
            )
        }
        DevelopedRoll(current.number, uris, failed)
    }

    class DevelopedRoll(val number: Int, val uris: List<android.net.Uri>, val failed: Int)

    /* ---------------- persistence ---------------- */

    private fun readLoadedRoll(): Roll? {
        val number = prefs.getInt(KEY_CURRENT, -1)
        if (number < 0) return null
        val length = prefs.getInt(KEY_LENGTH, 24)
        val started = prefs.getLong(KEY_STARTED, System.currentTimeMillis())
        val exposures = readIndex(number)
        recover(number, exposures)
        return Roll(number, length, started, exposures)
    }

    private fun readIndex(number: Int): List<Exposure> {
        val file = indexFile(number)
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines().mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size < 6) return@mapNotNull null
                val frame = File(dirFor(number), parts[5])
                if (!frame.exists()) return@mapNotNull null
                Exposure(
                    index = parts[0].toIntOrNull() ?: return@mapNotNull null,
                    file = frame,
                    takenAt = parts[1].toLongOrNull() ?: 0L,
                    filterId = parts[2],
                    width = parts[3].toIntOrNull() ?: 0,
                    height = parts[4].toIntOrNull() ?: 0,
                )
            }.sortedBy { it.index }
        }.getOrDefault(emptyList())
    }

    /**
     * Delete frames the index doesn't know about.
     *
     * These are the frames that were written when the process died before the index line
     * landed. Keeping them would be worse than losing them: they would develop out of order
     * and the counter would disagree with the roll.
     */
    private fun recover(number: Int, known: List<Exposure>) {
        val expected = known.map { it.file.name }.toSet()
        runCatching {
            dirFor(number).listFiles()?.forEach { file ->
                if (file.name == "index.txt") return@forEach
                if (file.name !in expected) {
                    Log.w(TAG, "discarding orphaned frame ${file.name}")
                    file.delete()
                }
            }
        }
    }

    private fun frameName(index: Int): String = "frame-%02d.jpg".format(index)

    private companion object {
        const val TAG = "FilmRoll"
        const val KEY_CURRENT = "current"
        const val KEY_LENGTH = "length"
        const val KEY_STARTED = "started"
        const val KEY_DEVELOPED = "developed"
    }
}
