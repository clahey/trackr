package net.clahey.trackr.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.clahey.trackr.data.TrackrRepository
import net.clahey.trackr.domain.StarterCategoryInput
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(private val repository: TrackrRepository) : ViewModel() {

    // Number of starter categories created by the most recent add (null = nothing to report yet).
    private val _addedCount = MutableStateFlow<Int?>(null)
    val addedCount: StateFlow<Int?> = _addedCount.asStateFlow()

    // @spec CAT-UI-090
    fun addStarterCategories(specs: List<StarterCategoryInput>) {
        viewModelScope.launch { _addedCount.value = repository.addStarterCategories(specs) }
    }

    fun consumeAddedCount() { _addedCount.value = null }
}
