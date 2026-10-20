package si.lukabencina.kilometrina

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import si.lukabencina.kilometrina.KilometrinaApplication
import si.lukabencina.kilometrina.data.*

@RunWith(AndroidJUnit4::class)
class MigrationSafetyTest {
  private val runId=System.nanoTime()
  private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
  private val dir get()=File(context.getExternalFilesDir(null),"release-regression").apply{mkdirs()}
  private val migrations get()=(1..5).map{v-> AppDatabase::class.java.getDeclaredField("MIGRATION_${v}_${v+1}").apply{isAccessible=true}.get(null) as Migration}
  private fun trip(id:Long=1)=TripEntity(id=id,startTime=1790841600000L,endTime=1790845200000L,startLat=46.0,startLon=14.0,startAddress="Škofja Loka",endAddress="Prodent",distanceMeters=12345.0,purpose="Servis",description="Žična oprema",routeStopsJson=TripRouteCodec.encode(listOf("Črnomelj","Žužemberk")),routeDetectionVersion=1,routeReviewPending=true,vehicleId="old",vehicleName="Old Škoda",registrationPlate="LJ-OLD",parkingCents=250,tollsCents=700,tripKind=TripKinds.PRIVATE)
  @Test fun freshDatabaseAndEveryLegacyUpgrade()=runBlocking<Unit> {
    val results=JSONArray()
    val freshName="migration-test-fresh-$runId.db"
    val fresh=Room.databaseBuilder(context,AppDatabase::class.java,freshName).build()
    try{fresh.tripDao().insertTrip(trip());assertEquals("Old Škoda",fresh.tripDao().getAllTrips().single().vehicleName);results.put(JSONObject().put("case","fresh-v6").put("pass",true))}finally{fresh.close();context.deleteDatabase(freshName)}
    for(v in 1..5){
      val name="migration-test-$runId-$v.db"
      try{
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object:SupportSQLiteOpenHelper.Callback(v){
          override fun onCreate(db:SupportSQLiteDatabase){
            db.execSQL("CREATE TABLE trips (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,startTime INTEGER NOT NULL,endTime INTEGER,startLat REAL NOT NULL,startLon REAL NOT NULL,startAddress TEXT NOT NULL,endLat REAL,endLon REAL,endAddress TEXT,distanceMeters REAL NOT NULL,purpose TEXT NOT NULL,ratePerKm REAL NOT NULL,tollsCents INTEGER NOT NULL,parkingCents INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE location_points (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,tripId INTEGER NOT NULL,timestamp INTEGER NOT NULL,lat REAL NOT NULL,lon REAL NOT NULL,accuracyMeters REAL NOT NULL,segmentMeters REAL NOT NULL,FOREIGN KEY(tripId) REFERENCES trips(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX index_location_points_tripId ON location_points(tripId)")
            db.execSQL("INSERT INTO trips VALUES (1,1000,2000,46.0,14.0,'Škofja Loka',46.1,14.1,'Prodent',12345.0,'Žična oprema',0.43,700,250)")
            db.execSQL("INSERT INTO location_points VALUES(1,1,1500,46.0,14.0,5.0,3.0)")
            migrations.filter{it.endVersion<=v}.forEach{it.migrate(db)}
          }
          override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int){}
        }).build())
        helper.writableDatabase;helper.close()
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(*migrations.toTypedArray()).build()
        try{val restored=db.tripDao().getAllTrips().single();assertEquals("Škofja Loka",restored.startAddress);assertEquals(12345.0,restored.distanceMeters,0.0001);assertEquals(700,restored.tollsCents);assertEquals(250,restored.parkingCents);assertEquals(1,db.tripDao().getAllPoints().size);assertTrue(restored.routeStops().isEmpty());results.put(JSONObject().put("case","$v-to-6").put("pass",true))}finally{db.close()}
      }catch(e:Throwable){results.put(JSONObject().put("case","$v-to-6").put("pass",false).put("error",e.toString()))}finally{context.deleteDatabase(name)}
    }
    File(dir,"migration-results.json").writeText(results.toString(2))
    assertEquals(6,results.length()); assertTrue((0 until results.length()).all { results.getJSONObject(it).getBoolean("pass") })
  }
}
