package tech.vauldex.dekita.ui.discover

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tech.vauldex.dekita.R
import tech.vauldex.dekita.domain.PlaceKind
import tech.vauldex.dekita.presentation.NO_PLACES_FOUND
import tech.vauldex.dekita.presentation.PLACE_CHIP_LABEL
import tech.vauldex.dekita.presentation.PLACE_PICKER_DETAIL
import tech.vauldex.dekita.presentation.PLACE_PICKER_TITLE
import tech.vauldex.dekita.presentation.PLACE_SEARCH_HINT
import tech.vauldex.dekita.presentation.place.PlacePickerUiState
import tech.vauldex.dekita.ui.theme.Dimens

/**
 * The first thing on Discover: the spot that "near" is measured from. A pin, the "Takeout near" label with the
 * place name under it, and an arrow. Tapping it opens [PlacePickerSheet].
 */
@Composable
internal fun LocationChip(placeName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .heightIn(min = Dimens.MinTouchTarget)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClickLabel = "Change location", role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$PLACE_PICKER_TITLE: $placeName. Tap to change" }
            .padding(horizontal = 8.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_place),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f, fill = false)) {
            Text(PLACE_CHIP_LABEL, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(placeName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            painterResource(R.drawable.ic_expand_more),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The picker behind the chip, as a bottom sheet: a title, what the spot is used for, a search field and the saved
 * places. The search only narrows that list. Picking a place closes the sheet first, then calls [onSelect];
 * dismissing it any other way calls [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlacePickerSheet(
    state: PlacePickerUiState,
    onQueryChange: (String) -> Unit,
    onSelect: (placeId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // The field keeps its own text so typing never waits on the view model, as the search field on Discover does.
    var searchText by rememberSaveable { mutableStateOf(state.query) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(PLACE_PICKER_TITLE, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(
                    PLACE_PICKER_DETAIL,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PlaceSearchField(
                text = searchText,
                onTextChange = {
                    searchText = it
                    onQueryChange(it)
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.hasNoMatches) {
                Text(
                    NO_PLACES_FOUND,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
            Column(Modifier.selectableGroup()) {
                state.places.forEach { place ->
                    PlaceRow(
                        name = place.name,
                        subtitle = place.subtitle,
                        icon = place.kind.icon,
                        selected = place.id == state.selected.id,
                        onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onSelect(place.id) }
                        },
                    )
                }
            }
        }
    }
}

/** A rounded search field. It filters the saved places by name and second line; the mockup has no address lookup. */
@Composable
private fun PlaceSearchField(text: String, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = text,
        onValueChange = onTextChange,
        placeholder = { Text(PLACE_SEARCH_HINT) },
        leadingIcon = {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingIcon = if (text.isNotEmpty()) {
            {
                IconButton(onClick = { onTextChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear search")
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
    )
}

/** One saved place: its icon, name and second line, and the single-choice mark. The whole row is the tap target. */
@Composable
private fun PlaceRow(name: String, subtitle: String, @DrawableRes icon: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // The row is the control, so the radio only draws the state and takes no taps of its own.
        RadioButton(selected = selected, onClick = null)
    }
}

private val PlaceKind.icon: Int
    @DrawableRes get() = when (this) {
        PlaceKind.CURRENT -> R.drawable.ic_near_me
        // The recent place in the mockup is a station, as in the design.
        PlaceKind.RECENT -> R.drawable.ic_train
        PlaceKind.HOME -> R.drawable.ic_home
        PlaceKind.OFFICE -> R.drawable.ic_work
    }
