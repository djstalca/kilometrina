package si.lukabencina.kilometrina.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TripEntity::class, LocationPointEntity::class, AttachmentEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN vehicleId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE trips ADD COLUMN vehicleName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE trips ADD COLUMN registrationPlate TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN tripKind TEXT NOT NULL DEFAULT 'BUSINESS'")
                db.execSQL("ALTER TABLE trips ADD COLUMN gpsQuality TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE trips ADD COLUMN gpsWarning TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE trips ADD COLUMN calendarEventId INTEGER")
                db.execSQL("ALTER TABLE trips ADD COLUMN calendarTitle TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS attachments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        tripId INTEGER NOT NULL,
                        displayName TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        data BLOB NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(tripId) REFERENCES trips(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_tripId ON attachments(tripId)")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "kilometrina.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
