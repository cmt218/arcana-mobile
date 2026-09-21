@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package org.arcana.mobile.discover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Stone
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectInset
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.NSString
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKAnnotationViewCollisionMode
import platform.MapKit.MKClusterAnnotation
import platform.MapKit.MKCoordinateRegion
import platform.MapKit.MKCoordinateRegionMake
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKCoordinateSpanMake
import platform.MapKit.MKFeatureDisplayPriorityRequired
import platform.MapKit.MKFeatureVisibility
import platform.MapKit.MKMapTypeMutedStandard
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKMarkerAnnotationView
import platform.MapKit.MKPointAnnotation
import platform.MapKit.MKPointOfInterestFilter
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIFontWeightBold
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIImage
import platform.UIKit.accessibilityLabel
import platform.UIKit.drawAtPoint
import platform.UIKit.isAccessibilityElement
import platform.UIKit.sizeWithAttributes
import platform.UIKit.UIEdgeInsetsMake
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val PIN_VIEW = "studio"
private const val CLUSTER_VIEW = "cluster"
private const val CLUSTERING_ID = "studios"
private const val PIN_CLEARANCE = 28.0
// A pin's balloon is about 27pt across: a group is visibly the larger mark.
private const val CLUSTER_SMALL = 38.0
private const val CLUSTER_LARGE = 44.0
private const val CLUSTER_LARGE_FROM = 10
private const val CLUSTER_HALO = 5.0
private const val CLUSTER_HALO_ALPHA = 0.22
private const val CLUSTER_RING = 2.0
private const val CLUSTER_TEXT = 16.0

@Composable
actual fun StudioMap(
    pins: List<DiscoverPin>,
    selectedPinId: Int?,
    pinsEpoch: Int,
    focusEpoch: Int,
    camera: MapCameraMemory,
    onPinTapped: (Int) -> Unit,
    onMapTapped: () -> Unit,
    modifier: Modifier,
    bottomInset: Dp,
) {
    val pinTapped by rememberUpdatedState(onPinTapped)
    val mapTapped by rememberUpdatedState(onMapTapped)
    val controller = remember { MapController(camera, onPin = { pinTapped(it) }, onNothing = { mapTapped() }) }
    UIKitView(
        factory = { controller.makeMap() },
        modifier = modifier,
        update = { map -> controller.sync(map, pins, selectedPinId, pinsEpoch, focusEpoch, bottomInset.value.toDouble()) },
        properties = UIKitInteropProperties(
            // The map owns its gestures outright: no wait to see whether Compose wants the touch.
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true,
        ),
    )
}

/**
 * MapKit already keeps its logo and legal link inside the safe area, which on a
 * tab root includes the tab bar. Only what covers the map beyond that (the pin
 * card) is added, or the bar is counted twice and the logo strands mid-map.
 */
private class StudioMapView : MKMapView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    /** How much of the map's bottom edge Compose draws over, in points. */
    var coveredBottom = 0.0
        set(value) {
            if (field == value) return
            field = value
            applyMargins()
        }

    /** Runs once, the first time the map has a size. */
    var onSized: (() -> Unit)? = null

    val hasSize: Boolean get() = bounds.useContents { size.width > 0.0 && size.height > 0.0 }

    override fun layoutSubviews() {
        super.layoutSubviews()
        val sized = onSized ?: return
        if (!hasSize) return
        onSized = null
        // Not from inside MapKit's own layout pass.
        dispatch_async(dispatch_get_main_queue()) { sized() }
    }

    override fun safeAreaInsetsDidChange() {
        super.safeAreaInsetsDidChange()
        applyMargins()
    }

    private fun applyMargins() {
        val beyondSafeArea = maxOf(0.0, coveredBottom - safeAreaInsets.useContents { bottom })
        layoutMargins = UIEdgeInsetsMake(0.0, 0.0, beyondSafeArea, 0.0)
    }
}

private class StudioAnnotation(val pin: DiscoverPin) : MKPointAnnotation() {
    init {
        setCoordinate(CLLocationCoordinate2DMake(pin.latitude, pin.longitude))
        // Never drawn (the mark hides its title); it is what VoiceOver reads.
        setTitle(pinAccessibilityLabel(pin))
    }
}

/** Holds what outlives a recomposition: the delegate (MapKit keeps it weakly) and the annotations on the map. */
private class MapController(private val camera: MapCameraMemory, onPin: (Int) -> Unit, onNothing: () -> Unit) {
    private val delegate = MapDelegate(camera, onPin, onNothing)
    private val annotations = HashMap<Int, StudioAnnotation>()

    /** The selection [standAlone] last ran for: it runs once per change, not per update. */
    private var settledFor: Int? = null

    fun makeMap(): MKMapView = StudioMapView().apply {
        mapType = MKMapTypeMutedStandard
        pointOfInterestFilter = MKPointOfInterestFilter.filterExcludingAllCategories()
        showsCompass = false
        pitchEnabled = false
        rotateEnabled = false
        setRegion(rememberedRegion() ?: openingRegion(), animated = false)
        delegate = this@MapController.delegate
    }

    /**
     * A selected pin hidden inside a group comes out of it by leaving the map and
     * coming back with a new view. Its clustering identifier is only ever set in
     * pinView: clearing it on a live view crashes MapKit ("key cannot be nil").
     */
    private fun standAlone(map: MKMapView, target: StudioAnnotation?) {
        val previous = delegate.aloneId?.let { annotations[it] }
        if (previous != null && previous === target) {
            // Singled out before its view existed; one made earlier still carries the group's id.
            if (map.viewForAnnotation(target)?.clusteringIdentifier != null) readd(map, target)
            return
        }
        delegate.aloneId = null
        if (previous != null) readd(map, previous)
        // A pin with a view of its own is already on show; only one inside a group comes out.
        if (target != null && map.viewForAnnotation(target) == null) {
            delegate.aloneId = target.pin.locationId
            readd(map, target)
        }
    }

    private fun readd(map: MKMapView, annotation: StudioAnnotation) {
        map.removeAnnotation(annotation)
        map.addAnnotation(annotation)
    }

    private fun show(map: MKMapView, frame: MapFrame, animated: Boolean) {
        val region = MKCoordinateRegionMake(
            CLLocationCoordinate2DMake(frame.latitude, frame.longitude),
            MKCoordinateSpanMake(frame.latitudeDelta, frame.longitudeDelta),
        )
        map.setRegion(map.regionThatFits(region), animated = animated)
    }

    private fun rememberedRegion(): CValue<MKCoordinateRegion>? {
        val latitude = camera.latitude ?: return null
        val longitude = camera.longitude ?: return null
        return MKCoordinateRegionMake(
            CLLocationCoordinate2DMake(latitude, longitude),
            MKCoordinateSpanMake(camera.latitudeDelta ?: return null, camera.longitudeDelta ?: return null),
        )
    }

    /**
     * Compose hands over the first update before UIKit has laid the map out, and
     * MapKit must not be driven at 0x0 (animated region, regrouping, animated select).
     * The annotations go in early; the camera and the selection wait for a size.
     */
    fun sync(map: MKMapView, pins: List<DiscoverPin>, selectedPinId: Int?, pinsEpoch: Int, focusEpoch: Int, bottomInset: Double) {
        map as StudioMapView
        map.coveredBottom = bottomInset
        reconcile(map, pins)
        if (map.hasSize) {
            map.onSized = null
            drive(map, pins, selectedPinId, pinsEpoch, focusEpoch, bottomInset, animated = true)
        } else {
            // Its view is made without a group from the start, so nothing has to be pulled out later.
            delegate.aloneId = selectedPinId
            map.onSized = { drive(map, pins, selectedPinId, pinsEpoch, focusEpoch, bottomInset, animated = false) }
        }
    }

    private fun reconcile(map: MKMapView, pins: List<DiscoverPin>) {
        val wanted = pins.associateBy { it.locationId }
        val gone = annotations.keys - wanted.keys
        if (gone.isNotEmpty()) {
            map.removeAnnotations(gone.mapNotNull { annotations.remove(it) })
        }
        val added = wanted.filterKeys { it !in annotations }.values.map { StudioAnnotation(it) }
        added.forEach { annotations[it.pin.locationId] = it }
        if (added.isNotEmpty()) map.addAnnotations(added)
    }

    private fun drive(
        map: MKMapView, pins: List<DiscoverPin>, selectedPinId: Int?,
        pinsEpoch: Int, focusEpoch: Int, bottomInset: Double, animated: Boolean,
    ) {
        if (pinsEpoch != camera.framedEpoch) {
            camera.framedEpoch = pinsEpoch
            framePins(pins)?.let { show(map, it, animated) }
        }
        if (focusEpoch != camera.focusedEpoch) {
            camera.focusedEpoch = focusEpoch
            framePins(pins.filter { it.locationId == selectedPinId })?.let { show(map, it, animated) }
        }

        val current = (map.selectedAnnotations.firstOrNull() as? StudioAnnotation)?.pin?.locationId
        val target = selectedPinId?.let { annotations[it] }
        if (settledFor != selectedPinId) {
            settledFor = selectedPinId
            standAlone(map, target)
        }
        if (current != selectedPinId) {
            if (target == null) {
                map.selectedAnnotations.forEach { map.deselectAnnotation(it as MKAnnotationProtocol, animated = animated) }
            } else {
                map.selectAnnotation(target, animated = animated)
            }
        }
        // The camera stays where the member left it unless the card would cover the pin.
        if (target != null) {
            val y = map.convertCoordinate(target.coordinate, toPointToView = map).useContents { this.y }
            val visibleBottom = map.bounds.useContents { size.height } - bottomInset - PIN_CLEARANCE
            if (y > visibleBottom) map.setCenterCoordinate(target.coordinate, animated = animated)
        }
    }
}

private fun openingRegion() = MKCoordinateRegionMakeWithDistance(
    CLLocationCoordinate2DMake(DiscoverMapDefaults.LATITUDE, DiscoverMapDefaults.LONGITUDE),
    DiscoverMapDefaults.SPAN_METERS, DiscoverMapDefaults.SPAN_METERS,
)

private class MapDelegate(
    private val camera: MapCameraMemory,
    private val onPin: (Int) -> Unit,
    private val onNothing: () -> Unit,
) : NSObject(), MKMapViewDelegateProtocol {

    private val clusterIcons = ClusterIcons()

    /** The selected pin's id: its view carries no clustering identifier. */
    var aloneId: Int? = null

    override fun mapView(mapView: MKMapView, viewForAnnotation: MKAnnotationProtocol): MKAnnotationView? = when (viewForAnnotation) {
        is MKClusterAnnotation -> clusterView(mapView, viewForAnnotation)
        is StudioAnnotation -> pinView(mapView, viewForAnnotation)
        else -> null
    }

    /** One location: the platform's pin, with the brand's monogram. */
    private fun pinView(mapView: MKMapView, annotation: StudioAnnotation): MKAnnotationView {
        val view = (mapView.dequeueReusableAnnotationViewWithIdentifier(PIN_VIEW) as? MKMarkerAnnotationView)
            ?: MKMarkerAnnotationView(annotation = annotation, reuseIdentifier = PIN_VIEW)
        view.annotation = annotation
        view.markerTintColor = Moss.toUIColor()
        view.glyphTintColor = Stone.toUIColor()
        view.glyphText = annotation.pin.monogram
        view.canShowCallout = false
        view.titleVisibility = MKFeatureVisibility.MKFeatureVisibilityHidden
        view.displayPriority = MKFeatureDisplayPriorityRequired
        view.clusteringIdentifier = if (annotation.pin.locationId == aloneId) null else CLUSTERING_ID
        return view
    }

    /** Several locations: a larger round mark with a count, so a group never reads as one studio. */
    private fun clusterView(mapView: MKMapView, annotation: MKClusterAnnotation): MKAnnotationView {
        val view = mapView.dequeueReusableAnnotationViewWithIdentifier(CLUSTER_VIEW)
            ?: MKAnnotationView(annotation = annotation, reuseIdentifier = CLUSTER_VIEW)
        val count = annotation.memberAnnotations.size
        view.annotation = annotation
        view.image = clusterIcons.icon(count)
        view.canShowCallout = false
        view.displayPriority = MKFeatureDisplayPriorityRequired
        view.collisionMode = MKAnnotationViewCollisionMode.MKAnnotationViewCollisionModeCircle
        view.isAccessibilityElement = true
        view.accessibilityLabel = "$count studios"
        return view
    }

    /**
     * A pin pulled out of a group gets its view a beat after it was asked for;
     * select it then. Two rules, both learned from a crash in MapKit's cluster
     * collision pass: never act when nothing is selected (a cluster's view would
     * match, null == null), and never touch the map from inside this callback,
     * which MapKit makes in the middle of that pass.
     */
    override fun mapView(mapView: MKMapView, didAddAnnotationViews: List<*>) {
        val alone = aloneId ?: return
        val annotation = didAddAnnotationViews.firstNotNullOfOrNull { view ->
            ((view as? MKAnnotationView)?.annotation as? StudioAnnotation)?.takeIf { it.pin.locationId == alone }
        } ?: return
        dispatch_async(dispatch_get_main_queue()) {
            if (aloneId == alone && annotation !in mapView.selectedAnnotations) {
                mapView.selectAnnotation(annotation, animated = true)
            }
        }
    }

    override fun mapView(mapView: MKMapView, regionDidChangeAnimated: Boolean) {
        mapView.region.useContents {
            camera.latitude = center.latitude
            camera.longitude = center.longitude
            camera.latitudeDelta = span.latitudeDelta
            camera.longitudeDelta = span.longitudeDelta
        }
    }

    @ObjCSignatureOverride
    override fun mapView(mapView: MKMapView, didSelectAnnotationView: MKAnnotationView) {
        when (val annotation = didSelectAnnotationView.annotation) {
            is StudioAnnotation -> onPin(annotation.pin.locationId)
            is MKClusterAnnotation -> {
                mapView.deselectAnnotation(annotation, animated = false)
                mapView.showAnnotations(annotation.memberAnnotations, animated = true)
            }
            else -> Unit
        }
    }

    @ObjCSignatureOverride
    override fun mapView(mapView: MKMapView, didDeselectAnnotationView: MKAnnotationView) {
        if (didDeselectAnnotationView.annotation is StudioAnnotation && mapView.selectedAnnotations.isEmpty()) onNothing()
    }
}

/** Round Moss marks with a soft halo and a count, drawn once per count. */
private class ClusterIcons {
    private val cache = HashMap<Int, UIImage>()

    fun icon(count: Int): UIImage = cache.getOrPut(count) { draw(count) }

    private fun draw(count: Int): UIImage {
        val side = (if (count >= CLUSTER_LARGE_FROM) CLUSTER_LARGE else CLUSTER_SMALL) + CLUSTER_HALO * 2
        val label = count.toString()
        val attributes = mapOf<Any?, Any?>(
            NSFontAttributeName to UIFont.systemFontOfSize(CLUSTER_TEXT, weight = UIFontWeightBold),
            NSForegroundColorAttributeName to Stone.toUIColor(),
        )
        return UIGraphicsImageRenderer(size = CGSizeMake(side, side)).imageWithActions { _ ->
            val bounds = CGRectMake(0.0, 0.0, side, side)
            Moss.toUIColor().colorWithAlphaComponent(CLUSTER_HALO_ALPHA).setFill()
            UIBezierPath.bezierPathWithOvalInRect(bounds).fill()
            Stone.toUIColor().setFill()
            UIBezierPath.bezierPathWithOvalInRect(CGRectInset(bounds, CLUSTER_HALO, CLUSTER_HALO)).fill()
            Moss.toUIColor().setFill()
            UIBezierPath.bezierPathWithOvalInRect(CGRectInset(bounds, CLUSTER_HALO + CLUSTER_RING, CLUSTER_HALO + CLUSTER_RING)).fill()
            @Suppress("CAST_NEVER_SUCCEEDS")
            val text = label as NSString
            val (width, height) = text.sizeWithAttributes(attributes).useContents { width to height }
            text.drawAtPoint(CGPointMake((side - width) / 2, (side - height) / 2), withAttributes = attributes)
        }
    }
}

private fun Color.toUIColor(): UIColor =
    UIColor(red = red.toDouble(), green = green.toDouble(), blue = blue.toDouble(), alpha = alpha.toDouble())
