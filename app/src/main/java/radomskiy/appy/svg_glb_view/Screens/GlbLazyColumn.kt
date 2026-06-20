package radomskiy.appy.svg_glb_view.Screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import radomskiy.appy.svg_glb_view.ViewModels.LazyColumnViewModel

@Composable
fun GlbLazyColumn(viewModel: LazyColumnViewModel, navController: NavController){
    val context = LocalContext.current
    val listOfGlbs = context.assets.list("3D")?.toList() ?: emptyList()

    Box(
        modifier = Modifier
            .fillMaxSize()
    ){
        LazyColumnCreate(listOfGlbs, viewModel, navController)
    }
}