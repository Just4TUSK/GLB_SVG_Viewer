package radomskiy.appy.svg_glb_view.bottomNav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.createGraph
import radomskiy.appy.svg_glb_view.Screens.GlbLazyColumn
import radomskiy.appy.svg_glb_view.Screens.LogsScreen
import radomskiy.appy.svg_glb_view.Screens.SvgLazyColumn
import radomskiy.appy.svg_glb_view.Screens.TwoDScreen
import radomskiy.appy.svg_glb_view.ViewModels.LazyColumnViewModel

@Composable
fun MainScreen(){
    val navController = rememberNavController()
    val viewModel : LazyColumnViewModel = viewModel()
    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
//        bottomBar = {BottomNav(navController)}
    ) { innerPadding ->
        val graph =
            navController.createGraph(startDestination = Screen.TwoD.rout) {
                composable(route = Screen.TwoD.rout) {
                    TwoDScreen(viewModel, navController)
                }
                composable(route = Screen.SvgList.rout) {
                    SvgLazyColumn(viewModel, navController)
                }
                composable(route = Screen.GlbList.rout) {
                    GlbLazyColumn(viewModel, navController)
                }
                composable(route = Screen.Logs.rout) {
                    LogsScreen(viewModel)
                }
            }
        NavHost(
            navController = navController,
            graph = graph,
            modifier = Modifier.padding(innerPadding)
        )
    }
}