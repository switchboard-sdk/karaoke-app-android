package com.synervoz.karaokeapp.data

import java.io.Serializable
import kotlin.math.roundToInt

data class Song(
    var fileName: String = "",
    var displayName: String = "",
    var lyrics: String = "",
    var duration:String = "",
) : Serializable {

}