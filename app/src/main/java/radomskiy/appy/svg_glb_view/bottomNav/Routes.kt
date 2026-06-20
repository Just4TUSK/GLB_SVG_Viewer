package radomskiy.appy.svg_glb_view.bottomNav
import radomskiy.appy.svg_glb_view.R

sealed class Screen(val rout : String) {
    object TwoD : Screen("two_d_screen")
    object Logs : Screen("logs_screen")
    object SvgList : Screen("svg_list_screen")
    object GlbList : Screen("glb_list_screen")
}