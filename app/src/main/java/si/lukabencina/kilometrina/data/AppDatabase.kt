package si.lukabencina.kilometrina.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TripEntity::class, LocationPointEntity::class],
    version = 2,
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

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "kilometrina.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
