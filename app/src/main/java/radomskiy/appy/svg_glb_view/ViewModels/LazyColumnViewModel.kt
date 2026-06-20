package radomskiy.appy.svg_glb_view.ViewModels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import radomskiy.appy.svg_glb_view.Model.Marker

class LazyColumnViewModel : ViewModel() {
    private val _items = mutableStateListOf<Marker>()
    val items: List<Marker> = _items
    var selectedModel by mutableStateOf<String?>(null)
        private set
    var selectedView by mutableStateOf("2D")
        private set

    fun selectModel(model: String) {
        selectedModel = model
    }

    fun unselectModel(){
        selectedModel = null
    }

    fun setView(view: String) {
        selectedView = view
    }

    fun addItem(item: Marker){
        _items.add(item)
    }

    fun deleteItem(itemId: String) {
        _items.removeAll {
            it.id == itemId
        }
    }

    fun clearList(){
        _items.clear()
    }

}