package com.makay.cleaner.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Çöp kutusu indeksi. SharedPreferences üzerindeki tek-JSON oku-değiştir-yaz
 * yaklaşımının yerini alır: eşzamanlı yazmalar birbirini ezmez, süreç ölümünde
 * yarım JSON kalmaz.
 */
@Entity(tableName = "trash_items")
data class TrashItemEntity(
    @PrimaryKey val id: String,
    val originalPath: String,
    val trashPath: String,
    val displayName: String,
    val sizeBytes: Long,
    val deletedAt: Long,
    val category: String,
    val mediaStoreUri: String? = null,
    val mimeType: String? = null,
    /** Orijinal albüm/klasör (MediaStore RELATIVE_PATH biçiminde). */
    val relativePath: String? = null
)

@Dao
interface TrashItemDao {

    @Query("SELECT * FROM trash_items ORDER BY deletedAt DESC")
    fun all(): List<TrashItemEntity>

    @Query("SELECT * FROM trash_items WHERE id = :id LIMIT 1")
    fun byId(id: String): TrashItemEntity?

    @Query("SELECT * FROM trash_items WHERE deletedAt < :cutoff")
    fun expired(cutoff: Long): List<TrashItemEntity>

    @Query("SELECT trashPath FROM trash_items")
    fun allTrashPaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: TrashItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<TrashItemEntity>)

    @Query("DELETE FROM trash_items WHERE id = :id")
    fun deleteById(id: String)

    @Query("DELETE FROM trash_items")
    fun clear()

    @Query("SELECT COUNT(*) FROM trash_items")
    fun count(): Int
}

@Database(entities = [TrashItemEntity::class], version = 1, exportSchema = false)
abstract class RecycleBinDatabase : RoomDatabase() {

    abstract fun trashDao(): TrashItemDao

    companion object {
        @Volatile
        private var instance: RecycleBinDatabase? = null

        fun get(context: Context): RecycleBinDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RecycleBinDatabase::class.java,
                    "recycle_bin.db"
                )
                    // İndeks yardımcı veridir; bozulursa dosyalar yetim uzlaştırma
                    // ile temizlenir, kullanıcı verisi kaybolmaz.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
