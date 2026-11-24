package com.swordfish.lemuroid.app.shared.covers

import android.content.Context
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject
import java.text.Normalizer

/**
 * Makes cover art "just work".
 *
 * The library scan guesses a cover URL from the game's database name, but that name often
 * doesn't exactly match the file on the libretro thumbnail server (different region tags,
 * renamed files, no database match at all). For every game whose cover is missing, this
 * downloads the server's list of covers for that system and picks the closest name.
 */
object CoverFixer {
    private const val GITHUB_API = "https://api.github.com/repos/libretro-thumbnails"
    private const val GITHUB_RAW = "https://raw.githubusercontent.com/libretro-thumbnails"
    private const val INDEX_MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
    private const val VERSION = "v2"
    private val mutex = Mutex()

    suspend fun run(
        context: Context,
        db: RetrogradeDatabase,
        force: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext
        try {
            val prefs = context.getSharedPreferences("cover_fixer", Context.MODE_PRIVATE)
            val games = db.gameDao().selectAll()
            val indexes = HashMap<String, SystemCovers?>()

            for (game in games) {
                if (game.customCoverUri != null) continue
                val doneKey = "${VERSION}_${game.id}_${game.fileName.hashCode()}"
                if (!force && prefs.getBoolean(doneKey, false)) continue

                val system = runCatching { GameSystem.findById(game.systemId) }.getOrNull() ?: continue
                val serverSystem = if (system.id == SystemID.MAME2003PLUS) "MAME" else system.libretroFullName
                val repo = serverSystem.replace(" ", "_")
                // Offline or rate limited: leave the game alone and try again next launch.
                val covers = indexes.getOrPut(repo) { loadCovers(context, repo) } ?: continue

                val match =
                    covers.boxarts.bestMatch(game.title, game.fileName)?.let { "Named_Boxarts" to it }
                        ?: covers.titles.bestMatch(game.title, game.fileName)?.let { "Named_Titles" to it }

                if (match != null) {
                    val (folder, name) = match
                    val url = "$GITHUB_RAW/${encode(repo)}/master/$folder/${encode(name)}.png"
                    if (url != game.coverFrontUrl) {
                        Timber.d("CoverFixer: ${game.title} -> $folder/$name")
                        db.gameDao().update(game.copy(coverFrontUrl = url))
                    }
                }
                prefs.edit().putBoolean(doneKey, true).apply()
            }
        } catch (e: Exception) {
            Timber.e(e, "CoverFixer failed")
        } finally {
            mutex.unlock()
        }
    }

    private class SystemCovers(val boxarts: CoverIndex, val titles: CoverIndex)

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun loadCovers(
        context: Context,
        repo: String,
    ): SystemCovers? {
        val dir = File(context.filesDir, "cover_index_$VERSION").apply { mkdirs() }
        val cache = File(dir, repo.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".txt")
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < INDEX_MAX_AGE_MS
        if (!fresh) {
            downloadPaths(repo)?.takeIf { it.isNotEmpty() }?.let { cache.writeText(it.joinToString("\n")) }
        }
        if (!cache.exists()) return null
        val paths = cache.readLines()
        fun namesIn(folder: String) =
            paths.filter { it.startsWith("$folder/") && it.endsWith(".png") }
                .map { it.removePrefix("$folder/").removeSuffix(".png") }
        return SystemCovers(CoverIndex(namesIn("Named_Boxarts")), CoverIndex(namesIn("Named_Titles")))
    }

    /** Every file path in the system's libretro-thumbnails repository on GitHub. */
    private fun downloadPaths(repo: String): List<String>? {
        return try {
            val conn = URL("$GITHUB_API/${encode(repo)}/git/trees/master?recursive=1").openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "AndysEmulator")
            if (conn.responseCode !in 200..299) {
                Timber.e("CoverFixer: GitHub answered ${conn.responseCode} for $repo")
                conn.disconnect()
                return null
            }
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val tree = JSONObject(json).getJSONArray("tree")
            (0 until tree.length()).map { tree.getJSONObject(it).getString("path") }
        } catch (e: Exception) {
            Timber.e(e, "CoverFixer: couldn't list covers for $repo")
            null
        }
    }
}

/** Cover names for one system, searchable by a forgiving version of the title. */
class CoverIndex(private val names: List<String>) {
    private val byKey: Map<String, List<String>> = names.groupBy { key(it) }
    private val tokenized: List<Pair<String, Set<String>>> = names.map { it to tokens(key(it)) }

    fun bestMatch(
        title: String,
        fileName: String,
    ): String? {
        if (names.isEmpty()) return null
        val wanted = listOf(title, fileName.substringBeforeLast('.'))
        val regions = regionsOf(title) + regionsOf(fileName)

        for (w in wanted) {
            byKey[key(w)]?.let { return pickRegion(it, regions) }
        }

        var best: String? = null
        var bestScore = 0.0
        for (w in wanted) {
            val t = tokens(key(w))
            if (t.isEmpty()) continue
            for ((name, nt) in tokenized) {
                val score = dice(t, nt)
                if (score > bestScore || (score == bestScore && best != null && regionRank(name, regions) < regionRank(best, regions))) {
                    bestScore = score
                    best = name
                }
            }
        }
        if (bestScore < FUZZY_THRESHOLD || best == null) return null
        // Among equally named covers, still prefer the right region.
        return pickRegion(byKey[key(best)] ?: listOf(best), regions)
    }

    private fun pickRegion(
        candidates: List<String>,
        regions: List<String>,
    ): String = candidates.minBy { regionRank(it, regions) }

    private fun regionRank(
        name: String,
        preferred: List<String>,
    ): Int {
        val lower = name.lowercase()
        preferred.forEachIndexed { i, r -> if (lower.contains(r)) return i }
        DEFAULT_REGIONS.forEachIndexed { i, r -> if (lower.contains(r)) return 10 + i }
        return 100
    }

    private fun regionsOf(s: String): List<String> {
        val lower = s.lowercase()
        return DEFAULT_REGIONS.filter { lower.contains(it) }
    }

    companion object {
        private const val FUZZY_THRESHOLD = 0.72
        private val DEFAULT_REGIONS = listOf("usa", "world", "europe", "japan")

        fun key(raw: String): String {
            var s = Normalizer.normalize(raw, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
            s = s.lowercase()
            s = s.replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
            s = s.replace("&", " and ")
            // "Legend of Zelda, The - ..." and "The Legend of Zelda ..." should look the same.
            s = s.replace(Regex(",\\s*the\\b"), " ")
            s = s.replace(Regex("^the\\s+"), "")
            s = s.replace(Regex("[^a-z0-9]+"), " ").trim()
            return s
        }

        fun tokens(key: String): Set<String> = key.split(' ').filter { it.isNotBlank() && it != "the" }.toSet()

        fun dice(
            a: Set<String>,
            b: Set<String>,
        ): Double {
            if (a.isEmpty() || b.isEmpty()) return 0.0
            val common = a.count { it in b }
            return 2.0 * common / (a.size + b.size)
        }
    }
}
