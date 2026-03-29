package com.example.newaudio.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Direkte Subfolder + SongCount (eine Query).
 */
data class DirectSubFolderSongCount(
    val path: String,
    val songCount: Int
)

/**
 * Schlankes Modell für Listenansichten (Filebrowser, Listen).
 * Enthält alles, was Listen aktuell benötigen.
 */
data class SongMinimal(
    val path: String,
    val contentUri: String,
    val title: String,
    val artist: String,
    val duration: Long,
    val albumArtPath: String?,
    val parentPath: String,
    val filename: String,
)

@Dao
interface SongDao {

    // --- Inserts -------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: SongEntity)

    // --- Deletes ------------------------------------------------------------

    @Query("DELETE FROM songs WHERE path = :path")
    suspend fun deleteByPath(path: String)

    /**
     * Löscht alle Songs in diesem Ordner UND in allen Unterordnern.
     */
    @Query(
        """
        DELETE FROM songs
        WHERE parentPath = :folderPath
           OR parentPath LIKE :folderPath || '/%'
           OR path LIKE :folderPath || '/%'
        """
    )
    suspend fun deleteByFolder(folderPath: String)

    @Query("DELETE FROM songs")
    suspend fun clearAll()

    // --- Reaktive Abfragen (Flows) ------------------------------------------

    /**
     * ⚠️ Heavy: lädt komplette Entities. Nicht für Listen empfohlen.
     */
    @Deprecated("Use observeSongsInFolderMinimal for lists")
    @Query("SELECT * FROM songs WHERE parentPath = :parentPath ORDER BY title ASC")
    fun observeSongsInFolder(parentPath: String): Flow<List<SongEntity>>

    /**
     * ✅ Optimiert: lädt nur die benötigten Spalten.
     */
    @Query(
        """
        SELECT path, contentUri, title, artist, duration, albumArtPath, parentPath, filename
        FROM songs
        WHERE parentPath = :parentPath
        ORDER BY title ASC
        """
    )
    fun observeSongsInFolderMinimal(parentPath: String): Flow<List<SongMinimal>>

    /**
     * Direkte Unterordner (eine Ebene), die Songs enthalten.
     */
    @Query(
        """
        SELECT DISTINCT parentPath
        FROM songs
        WHERE parentPath LIKE :parentPath || '/%'
          AND parentPath NOT LIKE :parentPath || '/%/%'
        """
    )
    fun observeSubFolders(parentPath: String): Flow<List<String>>

    /**
     * Direkte Unterordner + SongCount in EINER Query.
     * (Alte Methode, zählt nur direkte Kinder)
     */
    @Query(
        """
        SELECT parentPath AS path, COUNT(*) AS songCount
        FROM songs
        WHERE parentPath LIKE :parentPath || '/%'
          AND parentPath NOT LIKE :parentPath || '/%/%'
        GROUP BY parentPath
        """
    )
    fun observeDirectSubFolderSongCounts(parentPath: String): Flow<List<DirectSubFolderSongCount>>

    /**
     * NEU: Liefert Counts für ALLE Unterordner im gesamten Baum.
     * Wird genutzt, um die Summe rekursiv zu berechnen.
     */
    @Query(
        """
        SELECT parentPath AS path, COUNT(*) AS songCount
        FROM songs
        WHERE parentPath LIKE :parentPath || '/%'
        GROUP BY parentPath
        """
    )
    fun observeAllSubFolderSongCounts(parentPath: String): Flow<List<DirectSubFolderSongCount>>

    // --- Nicht-reaktive Abfragen --------------------------------------------

    @Query("SELECT * FROM songs WHERE parentPath = :parentPath ORDER BY title ASC")
    suspend fun getSongsInFolderSync(parentPath: String): List<SongEntity>

    @Query("SELECT COUNT(*) FROM songs WHERE parentPath = :parentPath")
    suspend fun getSongCountInFolder(parentPath: String): Int

    /**
     * Heavy: Songs im ganzen Tree.
     */
    @Query("SELECT * FROM songs WHERE path LIKE :parentPath || '/%'")
    suspend fun getAllSongsInTree(parentPath: String): List<SongEntity>

    /**
     * ✅ Leicht: nur Pfade im Tree (für Sync/Vergleiche/Deletes).
     */
    @Query("SELECT path FROM songs WHERE path LIKE :parentPath || '/%'")
    suspend fun getAllSongPathsInTree(parentPath: String): List<String>

    @Query("SELECT COUNT(*) FROM songs WHERE path LIKE :parentPath || '/%'")
    fun getSongCountInTreeFlow(parentPath: String): Flow<Int>

    @Query(
        """
        SELECT DISTINCT parentPath
        FROM songs
        WHERE parentPath LIKE :parentPath || '/%'
        """
    )
    suspend fun getSubFolders(parentPath: String): List<String>

    // --- Misc ---------------------------------------------------------------

    @Query("SELECT * FROM songs WHERE path = :path")
    suspend fun getSongByPath(path: String): SongEntity?

    @Query("SELECT * FROM songs WHERE filename = :filename AND size = :size LIMIT 1")
    suspend fun findSongByFilenameAndSize(filename: String, size: Long): SongEntity?

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT path FROM songs")
    suspend fun getAllPaths(): List<String>

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun countAllSongs(): Int

    @Query("DELETE FROM songs WHERE path IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>)

    @Query(
        """
        UPDATE songs
        SET path = :newPath,
            parentPath = :newParentPath,
            filename = :newFilename
        WHERE path = :oldPath
        """
    )
    suspend fun updatePath(oldPath: String, newPath: String, newParentPath: String, newFilename: String)
}