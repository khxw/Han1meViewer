package com.yenaly.han1meviewer.logic.dao

import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yenaly.han1meviewer.logic.entity.ComicProgressEntity
import com.yenaly.han1meviewer.logic.entity.FavoriteEntity
import com.yenaly.han1meviewer.logic.entity.FavoriteGroupEntity
import com.yenaly.han1meviewer.logic.entity.HanimeAdvancedSearchHistoryEntity
import com.yenaly.han1meviewer.logic.entity.SearchHistoryEntity
import com.yenaly.han1meviewer.logic.entity.WatchHistoryEntity
import com.yenaly.yenaly_libs.utils.applicationContext

/**
 * @project Hanime1
 * @author Yenaly Liew
 * @time 2022/06/22 022 22:46
 */
@Database(
    entities = [SearchHistoryEntity::class,
        WatchHistoryEntity::class,
        HanimeAdvancedSearchHistoryEntity::class,
        FavoriteEntity::class,
        FavoriteGroupEntity::class,
        ComicProgressEntity::class],
    version = 8, exportSchema = false
)
abstract class HistoryDatabase : RoomDatabase() {

    abstract val searchHistory: SearchHistoryDao

    abstract val watchHistory: WatchHistoryDao

    abstract val hanimeAdvancedSearchHistory: HanimeAdvancedSearchHistoryDao

    abstract val favorite: FavoriteDao

    abstract val favoriteGroup: FavoriteGroupDao

    abstract val comicProgress: ComicProgressDao

    companion object {
        val instance by lazy {
            Room.databaseBuilder(
                applicationContext,
                HistoryDatabase::class.java,
                "history.db"
            ).addMigrations(
                Migration1To2,
                Migration2To3,
                Migration3To4,
                Migration4To5,
                Migration5To6,
                Migration6To7,
                Migration7To8
            ).build()
        }
    }

    /** 新增漫画阅读进度表。 */
    object Migration7To8 : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `ComicProgressEntity` (
                    `comicId` TEXT NOT NULL,
                    `page` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`comicId`)
                )"""
            )
        }
    }

    object Migration1To2 : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {

            val cursor = db.query(
                """SELECT id, redirectLink FROM WatchHistoryEntity"""
            )
            while (cursor.moveToNext()) {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                val url = cursor.getString(cursor.getColumnIndexOrThrow("redirectLink"))
                val videoCode =
                    url.substringAfter("v=") // 不用 String.toVideoCode() 的原因是，防止該拓展函數因不可抗力改變導致 migrate 失敗
                val values = contentValuesOf("redirectLink" to videoCode)
                db.update(
                    "WatchHistoryEntity",
                    SQLiteDatabase.CONFLICT_REPLACE,
                    values,
                    "id = ?", arrayOf(id)
                )
            }
            db.execSQL(
                """ALTER TABLE WatchHistoryEntity
                   RENAME COLUMN redirectLink TO videoCode"""
            )
        }
    }
    object Migration2To3 : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 增加播放进度列，默认值为 0
            db.execSQL(
                """ALTER TABLE WatchHistoryEntity
                   ADD COLUMN progress INTEGER NOT NULL DEFAULT 0"""
            )
        }
    }
    object Migration3To4 : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `HanimeAdvancedSearchHistory` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `query` TEXT,
                    `genre` TEXT,
                    `sort` TEXT,
                    `broad` INTEGER,
                    `date` TEXT,
                    `duration` TEXT,
                    `tags` TEXT,
                    `brands` TEXT,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }
    object Migration4To5 : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 新增本地收藏（离线收藏）表
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `FavoriteEntity` (
                    `videoCode` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `coverUrl` TEXT NOT NULL,
                    `releaseDate` INTEGER NOT NULL,
                    `savedAt` INTEGER NOT NULL,
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
                )
                """.trimIndent()
            )
        }
    }
    object Migration5To6 : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 新增本地收藏分组表
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `FavoriteGroupEntity` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            // 收藏表新增分组 id 列，旧收藏自动归为未分组
            db.execSQL(
                """ALTER TABLE FavoriteEntity
                   ADD COLUMN groupId INTEGER"""
            )
        }
    }

    object Migration6To7 : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 继续观看需要总时长计算百分比；完成状态用于播放结束后移出列表。
            db.execSQL(
                """ALTER TABLE WatchHistoryEntity
                   ADD COLUMN duration INTEGER NOT NULL DEFAULT 0"""
            )
            db.execSQL(
                """ALTER TABLE WatchHistoryEntity
                   ADD COLUMN isCompleted INTEGER NOT NULL DEFAULT 0"""
            )
        }
    }
}



