package org.arcana.mobile.discover

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The platform's own map with a pin per studio location: MapKit on iOS, Google
 * Maps on Android. It opens on [DiscoverMapDefaults] and asks for no location
 * permission. A tap on a pin reports it; a tap on a cluster zooms into it; a
 * tap on bare map reports [onMapTapped].
 *
 * @param pinsEpoch a new value reframes the camera around [pins] (a filter changed them)
 * @param focusEpoch a new value sends the camera to the selected pin, wherever it was
 * @param camera where the member left the map; read once when the map is built, written as it moves
 * @param bottomInset what covers the map's bottom edge (the floating tab bar,
 *   the selected pin's card): the map's own logo and legal link stay above it,
 *   and a selected pin is centred in what is left
 */
@Composable
expect fun StudioMap(
    pins: List<DiscoverPin>,
    selectedPinId: Int?,
    pinsEpoch: Int,
    focusEpoch: Int,
    camera: MapCameraMemory,
    onPinTapped: (Int) -> Unit,
    onMapTapped: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
)
