package radomskiy.appy.svg_glb_view.Screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import org.json.JSONObject
import radomskiy.appy.svg_glb_view.Model.Marker
import radomskiy.appy.svg_glb_view.ViewModels.LazyColumnViewModel
import radomskiy.appy.svg_glb_view.Views.GLBViewer
import radomskiy.appy.svg_glb_view.Views.SVGViewer
import radomskiy.appy.svg_glb_view.bottomNav.Screen

@Composable
fun TwoDScreen(viewModel: LazyColumnViewModel, navController: NavController) {

    val selectedModel = viewModel.selectedModel
    val selectedView = viewModel.selectedView

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        if (selectedView == "2D" && selectedModel != null) {
            SVGViewer(
                Modifier,
                "SVGs/$selectedModel.svg",
                viewModel.items,
                onElementClicked = { json ->
                    val jsonObject = JSONObject(json)

                    when (jsonObject.getString("action")) {
                        "marker_added" -> {
                            val item = jsonParser(json)
                            viewModel.addItem(item)
                        }

                        "marker_removed" -> {
                            val id = jsonObject.getString("id")
                            viewModel.deleteItem(id)
                        }
                    }
                }
            )
        } else if (selectedView == "3D" && selectedModel != null) {
            GLBViewer(
                modifier = Modifier,
                model = selectedModel,
                damageIds = viewModel.items,
                onElementClicked = { json ->

                    val jsonObject = JSONObject(json)

                    when (jsonObject.getString("action")) {
                        "marker_added" -> {
                            val item = jsonParser(json)
                            viewModel.addItem(item)
                        }

                        "marker_removed" -> {
                            val id = jsonObject.getString("id")
                            viewModel.deleteItem(id)
                        }
                    }
                }
            )
        } else {
            Text(
                """
                    |Press + button to choose the model
                    |Press 2D button to change to 3D
                """.trimMargin(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(10.dp),
                fontSize = 21.sp
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    if (selectedView == "2D") {
                        navController.navigate(Screen.SvgList.rout)
                    } else {
                        navController.navigate(Screen.GlbList.rout)
                    }
                },
                modifier = Modifier
                    .padding(5.dp),
                shape = RectangleShape
            ) {
                Text(
                    "+",
                    fontSize = 21.sp
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            Button(
                onClick = {
                    if (selectedView == "2D") {
                        viewModel.setView("3D")
                    } else {
                        viewModel.setView("2D")
                    }
                },
                modifier = Modifier
                    .padding(5.dp),
                shape = RectangleShape
            ) {
                Text(
                    selectedView,
                    fontSize = 21.sp
                )
            }
        }

        Button(
            onClick = {
                navController.navigate(Screen.Logs.rout)
            },
            contentPadding = PaddingValues(5.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(5.dp),
            shape = RectangleShape
        ) {
            Text(
                "Logs",
                fontSize = 21.sp
            )
        }
    }
}

fun jsonParser(json: String): Marker {
    val jsonObject = JSONObject(json)
    val action = jsonObject.getString("action")
    val id = jsonObject.getString("id")
    val partName: String = jsonObject.getString("partName")
    val x = jsonObject.getDouble("x")
    val y = jsonObject.getDouble("y")
    val z = jsonObject.getDouble("z")
    val nx = jsonObject.optDouble("nx", 0.0)
    val ny = jsonObject.optDouble("ny", 0.0)
    val nz = jsonObject.optDouble("nz", 0.0)
    val nx_2d = jsonObject.optDouble("nx_2d", 0.0)
    val ny_2d = jsonObject.optDouble("ny_2d", 0.0)
    val viewType = jsonObject.optString("viewType", "")
    return Marker(action, id, partName, x, y, z, nx, ny, nz, nx_2d, ny_2d, viewType)
}


