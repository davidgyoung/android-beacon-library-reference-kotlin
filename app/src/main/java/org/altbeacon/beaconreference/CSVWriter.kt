package org.altbeacon.beaconreference

import Entries
import java.io.OutputStream
import org.altbeacon.beacon.Beacon
import java.io.FileOutputStream
import java.time.LocalDateTime

fun OutputStream.writeCsv(entries: Entries) {
    val writer = bufferedWriter()
    entries.entries.forEach {
        writer.write("\n${it.time}, ${it.tag.uuid} ,  ${it.tag.major} ,${it.tag.minor} ,  ${it.distance} m " )
    }
    writer.flush()
    }
