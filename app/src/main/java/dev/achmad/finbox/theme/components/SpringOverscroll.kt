package dev.achmad.finbox.theme.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** Drags past the ends and springs back, in place of the stock stretch. Horizontal only. */
@Composable
fun rememberSpringOverscrollEffect(): OverscrollEffect {
    val scope = rememberCoroutineScope()
    return remember(scope) { SpringOverscrollEffect(scope) }
}

/** How much of a drag the ends give way by. Lower is stiffer. */
private const val Give = 0.15f

internal class SpringOverscrollEffect(private val scope: CoroutineScope) : OverscrollEffect {

    private val displacement = Animatable(0f)

    override val isInProgress: Boolean get() = displacement.value != 0f

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        val current = displacement.value
        // Dragging back towards the content pays off the displacement before anything scrolls.
        val paidBack = if (abs(current) > 0.5f && sign(delta.x) != sign(current)) {
            val absorbed = if (abs(delta.x) <= abs(current)) delta.x else -current
            scope.launch { displacement.snapTo(current + absorbed) }
            Offset(absorbed, 0f)
        } else {
            Offset.Zero
        }

        val leftForScroll = delta - paidBack
        val consumed = performScroll(leftForScroll)
        val past = leftForScroll - consumed
        // Only a finger can pull past the end; a fling settles through applyToFling instead.
        if (abs(past.x) > 0.5f && source == NestedScrollSource.UserInput) {
            scope.launch { displacement.snapTo(displacement.value + past.x * Give) }
        }
        return paidBack + consumed
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        val left = velocity - performFling(velocity)
        displacement.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            initialVelocity = left.x * Give,
        )
    }

    override val node: DelegatableNode = object : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints,
        ): MeasureResult {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) {
                // Read in the placement block, so a new displacement only re-places the layer.
                placeable.placeRelativeWithLayer(displacement.value.roundToInt(), 0)
            }
        }
    }
}
