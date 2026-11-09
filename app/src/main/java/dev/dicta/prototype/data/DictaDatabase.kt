package dev.dicta.prototype.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="entries", indices=[Index(value=["cacheKey"] )])
data class StoredEntry(
    @PrimaryKey val id: String, val query: String, val createdAt: Long,
    val rawJson: String, val schemaJson: String, val templateJson: String,
    val profileRevision: String, val cacheKey: String,
    val title: String, val subtitle: String, val secondary: String, val badge: String,
    @ColumnInfo(defaultValue="1") val rendererVersion: Int = 1,
)
@Entity(tableName="history", foreignKeys=[ForeignKey(entity=StoredEntry::class,parentColumns=["id"],childColumns=["entryId"],onDelete=ForeignKey.RESTRICT)], indices=[Index("entryId"),Index("queriedAt")])
data class HistoryEvent(@PrimaryKey val id:String, val entryId:String, val source:String, val queriedAt:Long)
@Entity(tableName="bookmarks", foreignKeys=[ForeignKey(entity=StoredEntry::class,parentColumns=["id"],childColumns=["entryId"],onDelete=ForeignKey.RESTRICT)])
data class Bookmark(@PrimaryKey val entryId:String, val savedAt:Long)
@Entity(tableName="daily", foreignKeys=[ForeignKey(entity=StoredEntry::class,parentColumns=["id"],childColumns=["entryId"],onDelete=ForeignKey.RESTRICT)], indices=[Index("entryId")])
data class DailySelection(@PrimaryKey val slot:String, val entryId:String, val localDate:String, val zoneId:String, val selectedAt:Long, val refreshCounter:Int)
@Entity(tableName="profiles", indices=[Index(value=["profileId","revision"],unique=true)])
data class ProfileRevision(
    @PrimaryKey val id:String, val profileId:String, val revision:Int, val name:String,
    val prompt:String, val schema:String, val template:String, val example:String,
    val model:String, val contentHash:String, val createdAt:Long,
)
@Entity(tableName="preferences")
data class Preference(@PrimaryKey val name:String, val value:String)

data class HistoryRow(@Embedded val entry:StoredEntry, @ColumnInfo(name="eventId") val eventId:String, @ColumnInfo(name="queriedAt") val queriedAt:Long)

@Dao
interface DictaDao {
    @Query("SELECT entries.*, history.id AS eventId, history.queriedAt FROM history JOIN entries ON entries.id=history.entryId ORDER BY history.queriedAt DESC, history.id DESC")
    fun history():Flow<List<HistoryRow>>
    @Query("SELECT entries.* FROM entries JOIN bookmarks ON entries.id=bookmarks.entryId ORDER BY bookmarks.savedAt DESC, bookmarks.entryId DESC")
    fun vocabulary():Flow<List<StoredEntry>>
    @Query("SELECT * FROM profiles ORDER BY createdAt DESC") fun profiles():Flow<List<ProfileRevision>>
    @Query("SELECT * FROM preferences") fun preferences():Flow<List<Preference>>
    @Query("SELECT * FROM daily ORDER BY selectedAt DESC") fun daily():Flow<List<DailySelection>>
    @Query("SELECT * FROM entries WHERE id=:id") suspend fun entry(id:String):StoredEntry?
    @Query("SELECT * FROM entries WHERE cacheKey=:key ORDER BY createdAt DESC LIMIT 1") suspend fun cached(key:String):StoredEntry?
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(entry:StoredEntry)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(event:HistoryEvent)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(bookmark:Bookmark)
    @Insert suspend fun insert(profile:ProfileRevision)
    @Upsert suspend fun put(preference:Preference)
    @Upsert suspend fun put(daily:DailySelection)
    @Query("SELECT * FROM profiles WHERE id=:id") suspend fun profile(id:String):ProfileRevision?
    @Query("SELECT * FROM profiles WHERE profileId=:id ORDER BY revision DESC LIMIT 1") suspend fun latestProfile(id:String):ProfileRevision?
    @Query("SELECT * FROM preferences WHERE name=:name") suspend fun preference(name:String):Preference?
    @Query("DELETE FROM history WHERE id IN (:ids)") suspend fun deleteHistory(ids:List<String>)
    @Query("DELETE FROM bookmarks WHERE entryId IN (:ids)") suspend fun removeBookmarks(ids:List<String>)
    @Query("SELECT * FROM daily WHERE slot=:slot") suspend fun daily(slot:String):DailySelection?
    @Query("SELECT entries.title FROM entries JOIN daily ON entries.id=daily.entryId ORDER BY daily.selectedAt DESC LIMIT 20") suspend fun dailyWords():List<String>
}

@Database(entities=[StoredEntry::class,HistoryEvent::class,Bookmark::class,DailySelection::class,ProfileRevision::class,Preference::class], version=2, exportSchema=true)
abstract class DictaDatabase:RoomDatabase() {
    abstract fun dao():DictaDao
    companion object {
        // Earlier snapshot rows use renderer v1. Never reset data for a schema upgrade.
        val MIGRATION_1_2=object:Migration(1,2) {
            override fun migrate(db:SupportSQLiteDatabase) { db.execSQL("ALTER TABLE entries ADD COLUMN rendererVersion INTEGER NOT NULL DEFAULT 1") }
        }
        fun open(context:Context,name:String="dicta.db")=Room.databaseBuilder(context.applicationContext,DictaDatabase::class.java,name)
            .addMigrations(MIGRATION_1_2).build()
    }
}
