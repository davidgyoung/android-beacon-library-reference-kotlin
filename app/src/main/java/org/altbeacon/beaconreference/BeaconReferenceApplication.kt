package org.altbeacon.beaconreference

import android.app.Application
import android.app.NotificationManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.altbeacon.beacon.*
import org.altbeacon.beacon.powersave.BackgroundPowerSaver
import org.altbeacon.beacon.startup.BootstrapNotifier
import org.altbeacon.beacon.startup.RegionBootstrap
import org.altbeacon.bluetooth.BluetoothMedic

class BeaconReferenceApplication: Application(), BootstrapNotifier, RangeNotifier {
    val rangingData = RangingData()
    val monitoringData = MonitoringData()
    var alreadyStartedRangingAtBoot = false
    lateinit var region: Region
    lateinit var regionBootstrap: RegionBootstrap

    override fun onCreate() {
        super.onCreate()

        val beaconManager = BeaconManager.getInstanceForApplication(this)

        // By default the AndroidBeaconLibrary will only find AltBeacons.  If you wish to make it
        // find a different type of beacon, you must specify the byte layout for that beacon's
        // advertisement with a line like below.  The example shows how to find a beacon with the
        // same byte layout as AltBeacon but with a beaconTypeCode of 0xaabb.  To find the proper
        // layout expression for other beacon types, do a web search for "setBeaconLayout"
        // including the quotes.
        //
        //beaconManager.getBeaconParsers().clear();
        //beaconManager.getBeaconParsers().add(new BeaconParser().
        //        setBeaconLayout("m:0-1=4c00,i:2-24v,p:24-24"));


        // By default the AndroidBeaconLibrary will only find AltBeacons.  If you wish to make it
        // find a different type of beacon like Eddystone or iBeacon, you must specify the byte layout
        // for that beacon's advertisement with a line like below.
        //
        // If you don't care about AltBeacon, you can clear it from the defaults:
        //beaconManager.getBeaconParsers().clear()

        // The example shows how to find iBeacon.
        beaconManager.getBeaconParsers().add(
            BeaconParser().
                setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"))

        // enabling debugging will send lots of verbose debug information from the library to Logcat
        // this is useful for troubleshooting problmes
        // BeaconManager.setDebug(true)


        // The BluetoothMedic code here, if included, will watch for problems with the bluetooth
        // stack and optionally:
        // - send notifications on bluetooth problems
        // - power cycle bluetooth to recover on bluetooth problems
        // - periodically do a proactive scan or transmission to verify the bluetooth stack is OK
        BluetoothMedic.getInstance().setNotificationsEnabled(true, R.drawable.ic_launcher_background)
        BluetoothMedic.getInstance().enablePowerCycleOnFailures(this)
        BluetoothMedic.getInstance().enablePeriodicTests(this, BluetoothMedic.SCAN_TEST + BluetoothMedic.TRANSMIT_TEST)

        // simply constructing this class will automatically cause the library to save battery
        // whenever the application is not visible.  This reduces bluetooth power usage by about 60%
        // on Android 4-7.
        BackgroundPowerSaver(this)


        // If you want to continuously range beacons in the background more often than every 15 mintues,
        // you can use the library's built-in foreground service to unlock this behavior on Android
        // 8+.   the method below shows how you set that up.
        //setupForegroundService()

        // The code below will start "monitoring" for beacons matching the region definition below
        // the region definition is a wildcard that matches all beacons regardless of identifiers.
        // if you only want to detect becaonc with a specific UUID, change the id1 paremeter to
        // a UUID like "2F234454-CF6D-4A0F-ADF2-F4911BA9FFA6"
        region = Region("wildcard-region", null, null, null)
        regionBootstrap = RegionBootstrap(this, region)

        // Note that the RegionBootstrap is a specialized form of starting beacon monitoring that
        // is optimized for background detection.  Because this cocde is in a custom application
        // class, having the aove code here will detect beacons even after your app is killed or
        // your phone reboots.
        // If you don't need background detection, you can use a variant to the above call that looks
        // like this:
        // beaconManager.bind(this) /* You will need to make this class implement BeaconConsumer */
        // beaconManager.startMonitoringBeaconsInRegion(region); // put this in the onBeconServiceConnected callback

        // By default, the library will scan in the background every 5 minutes on Android 4-7,
        // which will be limited to scan jobs scheduled every ~15 minutes on Android 8+
        // If you want more frequent scanning (requires a foreground service on Android 8+),
        // configure that here:
        //beaconManager.setEnableScheduledScanJobs(false);
        //beaconManager.setBackgroundBetweenScanPeriod(0);
        //beaconManager.setBackgroundScanPeriod(1100);
    }

    fun disableMonitoring() {
        if (regionBootstrap != null) {
            regionBootstrap.disable()
        }
    }

    fun enableMonitoring() {
        regionBootstrap = RegionBootstrap(this, region)
    }

    fun setupForegroundService() {
        val builder = Notification.Builder(this, "BeaconReferenceApp")
        builder.setSmallIcon(R.drawable.ic_launcher_background)
        builder.setContentTitle("Scanning for Beacons")
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.setContentIntent(pendingIntent);
        val channel =  NotificationChannel("My Notification Channel ID",
            "My Notification Name", NotificationManager.IMPORTANCE_DEFAULT)
        channel.setDescription("My Notification Channel Description")
        val notificationManager =  getSystemService(
                Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel);
        builder.setChannelId(channel.getId());
        BeaconManager.getInstanceForApplication(this).enableForegroundServiceScanning(builder.build(), 456);
    }


    // The folowing three methods are from BoostrapNotifier

    override fun didDetermineStateForRegion(state: Int, region: Region?) {
        Log.d(TAG, "didDetermineStateForRegion")
        monitoringData.state.postValue(state)
        // This is a convenient place to start ranging if you want it on.
        if (region != null  && alreadyStartedRangingAtBoot == false) {
            alreadyStartedRangingAtBoot = true
            BeaconManager.getInstanceForApplication(this).startRangingBeaconsInRegion(region)
            BeaconManager.getInstanceForApplication(this).addRangeNotifier(this)
        }
    }

    override fun didEnterRegion(region: Region?) {
        // In this example, this class sends a notification to the user whenever a Beacon
        // matching a Region (defined above) are first seen.
        Log.d(TAG, "didEnterRegion")
        monitoringData.state.postValue(MonitorNotifier.INSIDE)
        sendNotification()
    }

    override fun didExitRegion(region: Region?) {
        Log.d(TAG, "didExitRegion")
        monitoringData.state.postValue(MonitorNotifier.OUTSIDE)
    }

    // The following method is from RangeNotifier

    override fun didRangeBeaconsInRegion(beacons: MutableCollection<Beacon>?, region: Region?) {
        // This method is called periodically with a list of all visible beacons matching the region
        if (beacons != null) {
            Log.d(TAG, "Detected ${beacons.count()} beacons:")
            for (beacon: Beacon in beacons) {
                Log.d(TAG, "$beacon about ${beacon.distance} meters away")
            }
        }
        rangingData.beacons.postValue(beacons)
    }

    private fun sendNotification() {
        val builder = NotificationCompat.Builder(this)
            .setContentTitle("Beacon Reference Application")
            .setContentText("A beacon is nearby.")
            .setSmallIcon(R.drawable.ic_launcher_background)
        val stackBuilder = TaskStackBuilder.create(this)
        stackBuilder.addNextIntent(Intent(this, MainActivity::class.java))
        val resultPendingIntent = stackBuilder.getPendingIntent(
            0,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.setContentIntent(resultPendingIntent)
        val notificationManager =
            this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, builder.build())
    }

    // This MutableLiveData mechanism is used for sharing centralized beacon data with the ViewControllers
    class RangingData : ViewModel() {
        val beacons: MutableLiveData<Collection<Beacon>> by lazy {
            MutableLiveData<Collection<Beacon>>()
        }
    }
    // This MutableLiveData mechanism is used for sharing centralized beacon data with the ViewControllers
    class MonitoringData : ViewModel() {
        val state: MutableLiveData<Int> by lazy {
            MutableLiveData<Int>()
        }
    }


    companion object {
        val TAG = "BeaconReference"
    }

}