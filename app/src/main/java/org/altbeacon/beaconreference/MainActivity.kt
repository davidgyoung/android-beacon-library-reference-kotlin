package org.altbeacon.beaconreference

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.MonitorNotifier

class MainActivity : AppCompatActivity() {
    lateinit var beaconListView: ListView
    lateinit var beaconCountTextView: TextView
    lateinit var monitoringButton: Button
    lateinit var rangingButton: Button
    lateinit var beaconReferenceApplication: BeaconReferenceApplication
    var alertDialog: AlertDialog? = null
    var neverAskAgainPermissions = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        beaconReferenceApplication = application as BeaconReferenceApplication

        // Set up a Live Data observer for beacon data
        val regionViewModel = BeaconManager.getInstanceForApplication(this).getRegionViewModel(beaconReferenceApplication.region)
        // observer will be called each time the monitored regionState changes (inside vs. outside region)
        regionViewModel.regionState.observe(this, monitoringObserver)
        // observer will be called each time a new list of beacons is ranged (typically ~1 second in the foreground)
        regionViewModel.rangedBeacons.observe(this, rangingObserver)
        rangingButton = findViewById<Button>(R.id.rangingButton)
        monitoringButton = findViewById<Button>(R.id.monitoringButton)
        beaconListView = findViewById<ListView>(R.id.beaconList)
        beaconCountTextView = findViewById<TextView>(R.id.beaconCount)
        beaconCountTextView.text = "No beacons detected"
        beaconListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, arrayOf("--"))
    }

    override fun onPause() {
        Log.d(TAG, "onPause")
        super.onPause()
    }
    override fun onResume() {
        Log.d(TAG, "onResume")
        super.onResume()
        checkPermissions()
    }

    val monitoringObserver = Observer<Int> { state ->
        var dialogTitle = "Beacons detected"
        var dialogMessage = "didEnterRegionEvent has fired"
        var stateString = "inside"
        if (state == MonitorNotifier.OUTSIDE) {
            dialogTitle = "No beacons detected"
            dialogMessage = "didExitRegionEvent has fired"
            stateString == "outside"
            beaconCountTextView.text = "Outside of the beacon region -- no beacons detected"
            beaconListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, arrayOf("--"))
        }
        else {
            beaconCountTextView.text = "Inside the beacon region."
        }
        Log.d(TAG, "monitoring state changed to : $stateString")
        val builder =
            AlertDialog.Builder(this)
        builder.setTitle(dialogTitle)
        builder.setMessage(dialogMessage)
        builder.setPositiveButton(android.R.string.ok, null)
        alertDialog?.dismiss()
        alertDialog = builder.create()
        alertDialog?.show()
    }

    val rangingObserver = Observer<Collection<Beacon>> { beacons ->
        Log.d(TAG, "Ranged: ${beacons.count()} beacons")
        if (BeaconManager.getInstanceForApplication(this).rangedRegions.size > 0) {
            beaconCountTextView.text = "Ranging enabled: ${beacons.count()} beacon(s) detected"
            beaconListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
                beacons
                    .sortedBy { it.distance }
                    .map { "${it.id1}\nid2: ${it.id2} id3:  rssi: ${it.rssi}\nest. distance: ${it.distance} m" }.toTypedArray())
        }
    }

    fun rangingButtonTapped(view: View) {
        val beaconManager = BeaconManager.getInstanceForApplication(this)
        if (beaconManager.rangedRegions.size == 0) {
            beaconManager.startRangingBeacons(beaconReferenceApplication.region)
            rangingButton.text = "Stop Ranging"
            beaconCountTextView.text = "Ranging enabled -- awaiting first callback"
        }
        else {
            beaconManager.stopRangingBeacons(beaconReferenceApplication.region)
            rangingButton.text = "Start Ranging"
            beaconCountTextView.text = "Ranging disabled -- no beacons detected"
            beaconListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, arrayOf("--"))
        }
    }

    fun monitoringButtonTapped(view: View) {
        var dialogTitle = ""
        var dialogMessage = ""
        val beaconManager = BeaconManager.getInstanceForApplication(this)
        if (beaconManager.monitoredRegions.size == 0) {
            beaconManager.startMonitoring(beaconReferenceApplication.region)
            dialogTitle = "Beacon monitoring started."
            dialogMessage = "You will see a dialog if a beacon is detected, and another if beacons then stop being detected."
            monitoringButton.text = "Stop Monitoring"

        }
        else {
            beaconManager.stopMonitoring(beaconReferenceApplication.region)
            dialogTitle = "Beacon monitoring stopped."
            dialogMessage = "You will no longer see dialogs when becaons start/stop being detected."
            monitoringButton.text = "Start Monitoring"
        }
        val builder =
            AlertDialog.Builder(this)
        builder.setTitle(dialogTitle)
        builder.setMessage(dialogMessage)
        builder.setPositiveButton(android.R.string.ok, null)
        alertDialog?.dismiss()
        alertDialog = builder.create()
        alertDialog?.show()

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for (i in 1..permissions.size-1) {
            Log.d(TAG, "onRequestPermissionResult for "+permissions[i]+":" +grantResults[i])
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                //check if user select "never ask again" when denying any permission
                if (!shouldShowRequestPermissionRationale(permissions[i])) {
                    neverAskAgainPermissions.add(permissions[i])
                }
            }
        }
    }


    fun checkPermissions() {
        // basepermissions are for M and higher
        var permissions = arrayOf( Manifest.permission.ACCESS_FINE_LOCATION)
        var permissionRationale ="This app needs fine location permission to detect beacons.  Please grant this now."
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = arrayOf( Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_SCAN)
            permissionRationale ="This app needs fine location permission, and bluetooth scan permission to detect beacons.  Please grant all of these now."
        }
        else if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if ((checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
                permissions = arrayOf( Manifest.permission.ACCESS_FINE_LOCATION)
                permissionRationale ="This app needs fine location permission to detect beacons.  Please grant this now."
            }
            else {
                permissions = arrayOf( Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                permissionRationale ="This app needs background location permission to detect beacons in the background.  Please grant this now."
            }
        }
        else if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions = arrayOf( Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            permissionRationale ="This app needs both fine location permission and background location permission to detect beacons in the background.  Please grant both now."
        }
        var allGranted = true
        for (permission in permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) allGranted = false;
        }
        if (!allGranted) {
            if (neverAskAgainPermissions.count() == 0) {
                val builder =
                    AlertDialog.Builder(this)
                builder.setTitle("This app needs permissions to detect beacons")
                builder.setMessage(permissionRationale)
                builder.setPositiveButton(android.R.string.ok, null)
                builder.setOnDismissListener {
                    requestPermissions(
                        permissions,
                        PERMISSION_REQUEST_FINE_LOCATION
                    )
                }
                builder.show()
            }
            else {
                val builder =
                    AlertDialog.Builder(this)
                builder.setTitle("Functionality limited")
                builder.setMessage("Since location and device permissions have not been granted, this app will not be able to discover beacons.  Please go to Settings -> Applications -> Permissions and grant location and device discovery permissions to this app.")
                builder.setPositiveButton(android.R.string.ok, null)
                builder.setOnDismissListener { }
                builder.show()
            }
        }
        else {
            if (android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                        val builder =
                            AlertDialog.Builder(this)
                        builder.setTitle("This app needs background location access")
                        builder.setMessage("Please grant location access so this app can detect beacons in the background.")
                        builder.setPositiveButton(android.R.string.ok, null)
                        builder.setOnDismissListener {
                            requestPermissions(
                                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                                PERMISSION_REQUEST_BACKGROUND_LOCATION
                            )
                        }
                        builder.show()
                    } else {
                        val builder =
                            AlertDialog.Builder(this)
                        builder.setTitle("Functionality limited")
                        builder.setMessage("Since background location access has not been granted, this app will not be able to discover beacons in the background.  Please go to Settings -> Applications -> Permissions and grant background location access to this app.")
                        builder.setPositiveButton(android.R.string.ok, null)
                        builder.setOnDismissListener { }
                        builder.show()
                    }
                }
            }
            else if (android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.S &&
                (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                        != PackageManager.PERMISSION_GRANTED)) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_SCAN)) {
                    val builder =
                        AlertDialog.Builder(this)
                    builder.setTitle("This app needs bluetooth scan permission")
                    builder.setMessage("Please grant scan permission so this app can detect beacons.")
                    builder.setPositiveButton(android.R.string.ok, null)
                    builder.setOnDismissListener {
                        requestPermissions(
                            arrayOf(Manifest.permission.BLUETOOTH_SCAN),
                            PERMISSION_REQUEST_BLUETOOTH_SCAN
                        )
                    }
                    builder.show()
                } else {
                    val builder =
                        AlertDialog.Builder(this)
                    builder.setTitle("Functionality limited")
                    builder.setMessage("Since bluetooth scan permission has not been granted, this app will not be able to discover beacons  Please go to Settings -> Applications -> Permissions and grant bluetooth scan permission to this app.")
                    builder.setPositiveButton(android.R.string.ok, null)
                    builder.setOnDismissListener { }
                    builder.show()
                }
            }
            else {
                if (android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                            val builder =
                                AlertDialog.Builder(this)
                            builder.setTitle("This app needs background location access")
                            builder.setMessage("Please grant location access so this app can detect beacons in the background.")
                            builder.setPositiveButton(android.R.string.ok, null)
                            builder.setOnDismissListener {
                                requestPermissions(
                                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                                    PERMISSION_REQUEST_BACKGROUND_LOCATION
                                )
                            }
                            builder.show()
                        } else {
                            val builder =
                                AlertDialog.Builder(this)
                            builder.setTitle("Functionality limited")
                            builder.setMessage("Since background location access has not been granted, this app will not be able to discover beacons in the background.  Please go to Settings -> Applications -> Permissions and grant background location access to this app.")
                            builder.setPositiveButton(android.R.string.ok, null)
                            builder.setOnDismissListener { }
                            builder.show()
                        }
                    }
                }
            }
        }

    }

    companion object {
        val TAG = "MainActivity"
        val PERMISSION_REQUEST_BACKGROUND_LOCATION = 0
        val PERMISSION_REQUEST_BLUETOOTH_SCAN = 1
        val PERMISSION_REQUEST_BLUETOOTH_CONNECT = 2
        val PERMISSION_REQUEST_FINE_LOCATION = 3
    }

}