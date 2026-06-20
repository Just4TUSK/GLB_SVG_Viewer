package radomskiy.appy.svg_glb_view.Screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import radomskiy.appy.svg_glb_view.ViewModels.LazyColumnViewModel
import radomskiy.appy.svg_glb_view.bottomNav.Screen

@Composable
fun SvgLazyColumn(viewModel: LazyColumnViewModel, navController: NavController){
    val context = LocalContext.current
    val listOfSvgs = context.assets.list("SVGs")?.toList() ?: emptyList()

    Box(
        modifier = Modifier
            .fillMaxSize()
    ){
        LazyColumnCreate(listOfSvgs, viewModel, navController)
    }
}

@Composable
fun LazyColumnCreate(listOfItems: List<String>, viewModel: LazyColumnViewModel, navController: NavController){
    Column(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Models",
                modifier = Modifier,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(listOfItems) { index, svg ->

                val selected = svg.substringBeforeLast('.')
                LazyColumnItem(
                    selected,
                    onClick = {
                        viewModel.selectModel(selected)
                        viewModel.clearList()
                        navController.navigate(Screen.TwoD.rout)
                    })
            }
        }
    }
}
@Composable
fun LazyColumnItem(selected: String, onClick: () -> Unit){
    Card(
        border = BorderStroke(1.dp, Color.Black),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ){
            Text(
                selected,
                fontSize = 54.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}