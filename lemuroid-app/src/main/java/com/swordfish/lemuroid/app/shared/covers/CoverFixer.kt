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
import java.net.URLDecoder
import java.net.URLEncoder
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
    private const val SERVER = "https://thumbnails.libretro.com"
    private const val INDEX_MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
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
            val indexes = HashMap<String, CoverIndex?>()

            for (game in games) {
                if (game.customCoverUri != null) continue
                val checkedKey = "ok_${game.id}_${game.fileName.hashCode()}"
                if (!force && prefs.getBoolean(checkedKey, false)) continue

                val current = game.coverFrontUrl
                if (current != null) {
                    when (exists(current)) {
                        true -> {
                            prefs.edit().putBoolean(checkedKey, true).apply()
                            continue
                        }
                        null -> continue // offline, try again next time
                        false -> Unit
                    }
                }

                val system = runCatching { GameSystem.findById(game.systemId) }.getOrNull() ?: continue
                val serverSystem = if (system.id == SystemID.MAME2003PLUS) "MAME" else system.libretroFullName
                val index = indexes.getOrPut(serverSystem) { loadIndex(context, serverSystem) } ?: continue

                val match = index.bestMatch(game.title, game.fileName) ?: continue
                val url = "$SERVER/${encode(serverSystem)}/Named_Boxarts/${encode(match)}.png"
                Timber.d("CoverFixer: ${game.title} -> $match")
                db.gameDao().update(game.copy(coverFrontUrl = url))
                prefs.edit().putBoolean(checkedKey, true).apply()
            }
        } catch (e: Exception) {
            Timber.e(e, "CoverFixer failed")
        } finally {
            mutex.unlock()
        }
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun exists(url: String): Boolean? {
        return try {
            val conn = URL(url.replaceFirst("http://", "https://")).openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            null
        }
    }

    private fun loadIndex(
        context: Context,
        serverSystem: String,
    ): CoverIndex? {
        val dir = File(context.filesDir, "cover_index").apply { mkdirs() }
        val cache = File(dir, serverSystem.replace(Regex("[^A-Za-z0-9]"), "_") + ".txt")
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < INDEX_MAX_AGE_MS
        if (!fresh) {
            val names = downloadNames(serverSystem)
            if (names != null && names.isNotEmpty()) {
                cache.writeText(names.joinToString("\n"))
            }
        }
        if (!cache.exists()) return null
        val names = cache.readLines().filter { it.isNotBlank() }
        return if (names.isEmpty()) null else CoverIndex(names)
    }

    private fun downloadNames(serverSystem: String): List<String>? {
        return try {
            val conn = URL("$SERVER/${encode(serverSystem)}/Named_Boxarts/").openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            Regex("href=\"([^\"?/]+)\\.png\"").findAll(html)
                .map { URLDecoder.decode(it.groupValues[1].replace("+", "%2B"), "UTF-8") }
                .distinct()
                .toList()
        } catch (e: Exception) {
            Timber.e(e, "CoverFixer: couldn't list covers for $serverSystem")
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
