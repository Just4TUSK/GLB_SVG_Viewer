package radomskiy.appy.svg_glb_view.Model

import kotlinx.serialization.Serializable

@Serializable
data class Marker(
    val action: String,
    val id:  String,
    val partName: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val nx: Double,
    val ny: Double,
    val nz: Double,
    val nx_2d: Double,
    val ny_2d: Double,
    val viewType: String
    )