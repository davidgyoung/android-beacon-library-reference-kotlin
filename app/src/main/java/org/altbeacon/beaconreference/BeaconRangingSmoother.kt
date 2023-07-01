package org.altbeacon.beaconreference

import org.altbeacon.beacon.Beacon
import java.util.ArrayList

/*
 * This class is used to smooth out the beacon ranging data to prevent periodic dropouts.  By
 * default, it will retain beacons in the list if detected in the past 10 secconds, but you can
 * adjust this with the smoothingWindowMillis property.
 *
 * To use this class, simply add it to your project and call a line like below wherever you get
 * an update of ranged beacons (in an observer or in didRangeBeacons):
 *
 *  val visibleBeacons = BeaconRangingSmoother.shared.add(beacons).visibleBeacons
 */
class BeaconRangingSmoother {
    private var beacons: ArrayList<Beacon> = ArrayList<Beacon>()
    var smoothingWindowMillis: Long = 10000
    var visibleBeacons: List<Beacon> = ArrayList<Beacon>()
        get() {
            var visible = ArrayList<Beacon>()
            for (beacon in beacons) {
                if (System.currentTimeMillis() - beacon.lastCycleDetectionTimestamp < smoothingWindowMillis) {
                    visible.add(beacon)
                }
            }
            return visible
        }
    fun add(detectedBeacons: Collection<Beacon>): BeaconRangingSmoother {
        for (beacon in detectedBeacons) {
            beacon.lastCycleDetectionTimestamp = System.currentTimeMillis()
            beacons.add(beacon)
        }
        return this
    }
    companion object {
        val TAG = "BeaconRangingSmoother"
        val shared = BeaconRangingSmoother()
    }
}