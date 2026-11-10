package io.github.inputbiscuit.dotboard.core.data

import io.github.inputbiscuit.dotboard.core.model.Board
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class CachedBoard(val board: Board, val fetchedAtMillis: Long)

interface BoardStore {
    suspend fun get(key: String): CachedBoard?
    suspend fun put(key: String, value: CachedBoard)
}

class InMemoryBoardStore : BoardStore {
    private val map = ConcurrentHashMap<String, CachedBoard>()
    override suspend fun get(key: String) = map[key]
    override suspend fun put(key: String, value: CachedBoard) {
        map[key] = value
    }
}

/** One JSON file per board, so cached boards survive the widget's process being killed. */
class FileBoardStore(private val dir: File) : BoardStore {
    private val memory = InMemoryBoardStore()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun get(key: String): CachedBoard? = memory.get(key) ?: withContext(Dispatchers.IO) {
        val file = fileFor(key)
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString(CachedBoard.serializer(), file.readText()) }.getOrNull()
    }?.also { memory.put(key, it) }

    override suspend fun put(key: String, value: CachedBoard) {
        memory.put(key, value)
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val tmp = File(dir, "${fileName(key)}.tmp")
            tmp.writeText(json.encodeToString(CachedBoard.serializer(), value))
            tmp.renameTo(fileFor(key))
        }
    }

    private fun fileFor(key: String) = File(dir, "${fileName(key)}.json")

    private fun fileName(key: String) = key.replace(Regex("[^A-Za-z0-9]"), "_")
}

/**
 * Caches boards per [BoardQuery.key] so widgets showing the same station share one API call.
 * Concurrent requests for the same key wait for a single fetch.
 */
class CachingDepartureRepository(
    private val delegate: DepartureRepository,
    private val store: BoardStore = InMemoryBoardStore(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val defaultMaxAgeMillis: Long = 60_000,
) : DepartureRepository {

    private val locks = ConcurrentHashMap<String, Mutex>()

    override suspend fun board(query: BoardQuery): Board = cached(query, defaultMaxAgeMillis).board

    /** A board no older than [maxAgeMillis]; fetches (once, shared) if the cached one is older. */
    suspend fun cached(query: BoardQuery, maxAgeMillis: Long): CachedBoard {
        fresh(query.key, maxAgeMillis)?.let { return it }
        return locks.getOrPut(query.key) { Mutex() }.withLock {
            // Another caller may have fetched while we waited for the lock.
            fresh(query.key, maxAgeMillis) ?: CachedBoard(delegate.board(query), clock()).also { store.put(query.key, it) }
        }
    }

    /** The last board fetched for [query], however old, without fetching. */
    suspend fun peek(query: BoardQuery): CachedBoard? = store.get(query.key)

    private suspend fun fresh(key: String, maxAgeMillis: Long): CachedBoard? =
        store.get(key)?.takeIf { clock() - it.fetchedAtMillis in 0..maxAgeMillis }
}
