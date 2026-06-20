package radomskiy.appy.svg_glb_view.bottomNav

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import radomskiy.appy.svg_glb_view.R
import radomskiy.appy.svg_glb_view.ui.theme.whiteNav

@Composable
fun BottomNav(
    navController : NavController
) {
    val selectedNavigationIndex = rememberSaveable {
        mutableIntStateOf(0)
    }
    val navigationItems = listOf(
        NavItem("2D", R.drawable.two_d, Screen.TwoD.rout),
        NavItem("Logs", R.drawable.logs, Screen.Logs.rout),
    )
    NavigationBar(
        containerColor = whiteNav
    ) {
        navigationItems.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selectedNavigationIndex.intValue == index,
                onClick = {
                    selectedNavigationIndex.intValue = index
                    navController.navigate(item.route)
                },
                icon = {
                    Icon(painter = painterResource(item.iconId), contentDescription = item.label)
                },
                label = {
                    Text(
                        item.label,
                        color = if(index == selectedNavigationIndex.intValue)
                            Color.Black
                        else Color.Gray
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    unselectedIconColor = Color.Gray
                )
            )
        }
    }
}