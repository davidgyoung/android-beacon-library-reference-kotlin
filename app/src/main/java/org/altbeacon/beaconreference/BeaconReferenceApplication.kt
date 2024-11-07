package org.altbeacon.beaconreference

import android.app.*
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import com.davidgyoungtech.beaconparsers.EddystoneUidBeaconParser
import com.davidgyoungtech.beaconparsers.EddystoneUrlBeaconParser
import com.davidgyoungtech.beaconparsers.IBeaconParser
import org.altbeacon.beacon.*

class BeaconReferenceApplication: Application() {
    // the region definition is a wildcard that matches all beacons regardless of identifiers.
    // if you only want to detect beacons with a specific UUID, change the id1 paremeter to
    // a UUID like Identifier.parse("2F234454-CF6D-4A0F-ADF2-F4911BA9FFA6")
    // Each BeaconRegion must specify a beacon parser, which defines what format of Bluetooth beacon
    // to match, e.g. (iBeacon, AltBeacon, Eddystone UID, etc.)
    // The first parameter uniqueId is a string key that is used to identify this region in the
    // library.  This string key must be unique for each region you define, and the same uniqueID
    // must be used to tell the library to start and stop beacon ranging and monitoring.
    val wildcardIBeaconRegion = BeaconRegion("everyIBeaconRegion", IBeaconParser() /* from com.davidgyoungtech:beacon-parsers:1.0 */, null, null, null)
    // Below are examples of other region definitions for other beacon formats
    val wildcardAltBeaconRegion = BeaconRegion("everyAltBeaconRegion", AltBeaconParser(), null, null, null)
    //val everyEddystoneUidBeaconRegion = BeaconRegion("everyEddystoneUidBeaconRegion", EddystoneUidBeaconParser(), null, null, null)
    //val everyEddystoneUrlBeaconRegion = BeaconRegion("everyEddystoneUrlBeaconRegion", EddystoneUrlBeaconParser(), null, null, null)
    val nonBeaconLayout = "s:0-15=4c052726-cd97-4dde-9356-212cc1327a84,i:16-17,i:18-19,i:20-21,p:-:-59"
    val nonBeaconBeaconRegion = BeaconRegion("nonBeaconRegion", BeaconParser("nonBeacon").setBeaconLayout(nonBeaconLayout), null, null, null)

    override fun onCreate() {
        super.onCreate()
        setupBeaconScanning()
    }

    fun setupBeaconScanning() {
        val beaconManager = BeaconManager.getInstanceForApplication(this)

        // The intent scan strategy configured below will set up Blutooth scanning to find beacons
        // that are delivered by Android Intent.  This works well for background detections.
        // If you don't need background detectsions, then you don't need to set any settings, and
        // can accept the default with is to use a service to do the scanning.  This works well for
        // beacon detections when the app is in the foreground.
        val settings = Settings(scanStrategy = Settings.IntentScanStrategy(), longScanForcingEnabled = true)
        // You can also use the library's built-in "foreground service" to scan for beacons while
        // the app is in the background using the code below.  This shows a notification to
        // the user that your app is running , and will require the ACCESS_BACKGROUND_LOCATION
        // permission to be granted by the user in advance.
        //val settings = getSettingsForForegroundServiceScanning()

        // This line below will apply the new beacon scanning settings immediately  Any individual
        // settings not specified on the new settings object will revert to defaults.
        // If you only want to make a partial change to the settings, without reverting to defaults
        // for any settings unspecified you may call `beaconManager.adjustSettings(settings)`
        beaconManager.replaceSettings(settings)

        // The code below will start "monitoring" and "ranging" for beacons matching the region
        // definition at the top of this file
        beaconManager.startMonitoring(nonBeaconBeaconRegion)
        beaconManager.startRangingBeacons(nonBeaconBeaconRegion)

        val transmitter = BeaconTransmitter(this,  BeaconParser("nonBeacon").setBeaconLayout(nonBeaconLayout))
        val beacon = Beacon.Builder().setId1("1").setId2("1").setId3("1").build()
        transmitter.startAdvertising(beacon)

        // Set up a Live Data observer so this Activity can get beacon data from the Application class
        val regionViewModel = BeaconManager.getInstanceForApplication(this).getRegionViewModel(wildcardIBeaconRegion)
        // observer will be called each time the monitored regionState changes (inside vs. outside region)
        regionViewModel.regionState.observeForever( centralMonitoringObserver)
        // observer will be called each time a new list of beacons is ranged (typically ~1 second in the foreground)
        regionViewModel.rangedBeacons.observeForever( centralRangingObserver)
    }

    fun getSettingsForForegroundServiceScanning(): Settings {

        val builder = Notification.Builder(this, "BeaconReferenceApp")
        builder.setSmallIcon(R.drawable.ic_launcher_background)
        builder.setContentTitle("Scanning for Beacons")
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT + PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(pendingIntent);
        val channel =  NotificationChannel("beacon-ref-notification-id",
            "My Notification Name", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "My Notification Channel Description"
        val notificationManager =  getSystemService(
            Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        builder.setChannelId(channel.id)
        val notification = builder.build()

        return Settings(
            scanStrategy = Settings.ForegroundServiceScanStrategy(
                notification, 456
            ),
            scanPeriods = Settings.ScanPeriods(1100, 0, 1100, 0)
        )
    }

    private val centralMonitoringObserver = Observer<Int> { state ->
        if (state == MonitorNotifier.OUTSIDE) {
            Log.d(TAG, "outside beacon region.")
        }
        else {
            Log.d(TAG, "inside beacon region.")
            sendNotification()
        }
    }

    private val centralRangingObserver = Observer<Collection<Beacon>> { beacons ->
        val rangeAgeMillis = System.currentTimeMillis() - (beacons.firstOrNull()?.lastCycleDetectionTimestamp ?: 0)
        if (rangeAgeMillis < 10000) {
            Log.d(MainActivity.TAG, "Ranged: ${beacons.count()} beacons")
            for (beacon: Beacon in beacons) {
                Log.d(TAG, "$beacon about ${beacon.distance} meters away")
            }
        }
        else {
            Log.d(MainActivity.TAG, "Ignoring stale ranged beacons from $rangeAgeMillis millis ago")
        }
    }

    private fun sendNotification() {
        val builder = NotificationCompat.Builder(this, "beacon-ref-notification-id")
            .setContentTitle("Beacon Reference Application")
            .setContentText("A beacon is nearby.")
            .setSmallIcon(R.drawable.ic_launcher_background)
        val stackBuilder = TaskStackBuilder.create(this)
        stackBuilder.addNextIntent(Intent(this, MainActivity::class.java))
        val resultPendingIntent = stackBuilder.getPendingIntent(
            0,
            PendingIntent.FLAG_UPDATE_CURRENT + PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(resultPendingIntent)
        val channel =  NotificationChannel("beacon-ref-notification-id",
            "My Notification Name", NotificationManager.IMPORTANCE_DEFAULT)
        channel.setDescription("My Notification Channel Description")
        val notificationManager =  getSystemService(
            Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel);
        builder.setChannelId(channel.getId());
        notificationManager.notify(1, builder.build())
    }

    companion object {
        const val TAG = "BeaconReference"
    }

}