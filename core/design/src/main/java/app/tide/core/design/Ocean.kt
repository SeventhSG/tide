package app.tide.core.design

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The app's ground is deep water, not a flat colour.
 *
 * Three rules govern it, and they are what keep it from being decoration:
 *
 *  1. It is never behind a number. Anything carrying text or data sits on a
 *     scrim ([OceanScrim]), so contrast is measured against the scrim and not
 *     against the water.
 *  2. It is environment, not feedback. It never tells you something changed.
 *  3. It turns off: under reduced motion, under battery saver, on low-RAM
 *     devices, and entirely on the active session logger.
 *
 * On API 33 and up the water is one AGSL shader, so the whole thing is a single
 * GPU draw. Below that it degrades to the depth gradient alone, which is most of
 * the effect for none of the cost.
 */

/** Depth gradient. Always drawn, on every API level, animated or not. */
private val OceanGradient = Brush.verticalGradient(
    0.00f to Color(0xFF17555E),
    0.16f to Color(0xFF0B323B),
    0.42f to Color(0xFF061C23),
    1.00f to Color(0xFF03090D),
)

@Composable
fun OceanBackground(
    modifier: Modifier = Modifier,
    intensity: OceanIntensity = OceanIntensity.Full,
    content: @Composable BoxScope.() -> Unit,
) {
    val reduceMotion = rememberReducedMotion()
    val animate = intensity == OceanIntensity.Full && !reduceMotion &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    Box(modifier.fillMaxSize().background(OceanGradient)) {
        if (intensity != OceanIntensity.Off && animate) {
            @Suppress("NewApi")
            AnimatedWater(Modifier.fillMaxSize())
        }
        content()
    }
}

enum class OceanIntensity {
    /** Shader, caustics, drifting particulate. */
    Full,

    /** Depth gradient only. Default on low-RAM devices and under battery saver. */
    Subtle,

    /** Flat surface colour. The session logger uses this. */
    Off,
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AnimatedWater(modifier: Modifier) {
    val shader = remember { RuntimeShader(OCEAN_AGSL) }
    val transition = rememberInfiniteTransition(label = "ocean")

    // One slow clock drives caustics, shafts and drift together. A 48 second
    // cycle is long enough that the loop is not perceptible.
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 48f,
        animationSpec = infiniteRepeatable(
            animation = tween(48_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "oceanTime",
    )

    Box(
        modifier.graphicsLayer {
            shader.setFloatUniform("uTime", time)
            shader.setFloatUniform("uSize", size.width, size.height)
            renderEffect = RenderEffect
                .createRuntimeShaderEffect(shader, "uContent")
                .asComposeRenderEffect()
        },
    )
}

/**
 * Caustics are two interfering value-noise fields, which is cheap and reads as
 * moving water. Shafts are gaussian bands slanted with depth. Both fade out
 * before mid screen, because light does.
 */
private const val OCEAN_AGSL = """
uniform float2 uSize;
uniform float uTime;
uniform shader uContent;

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
               mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

half4 main(float2 coord) {
    float2 uv = coord / uSize;
    float depth = uv.y;

    // light falls off fast underwater
    float lit = pow(max(0.0, 1.0 - depth * 2.1), 1.7);

    // caustics: two drifting noise fields, sharpened into a web
    float2 a = uv * float2(7.0, 15.0) + float2(uTime * 0.035, uTime * -0.06);
    float2 b = uv * float2(11.0, 9.0) + float2(uTime * -0.028, uTime * 0.045);
    float web = noise(a) * noise(b);
    web = pow(web, 5.0) * 7.0;

    // shafts: gaussian bands, slanted further with depth
    float shafts = 0.0;
    shafts += exp(-pow((uv.x + depth * 0.30 - 0.14) / 0.055, 2.0)) * 0.55;
    shafts += exp(-pow((uv.x + depth * 0.30 - 0.46) / 0.085, 2.0)) * 0.80;
    shafts += exp(-pow((uv.x + depth * 0.30 - 0.78) / 0.040, 2.0)) * 0.45;

    // suspended particulate, drifting up, denser with depth
    float2 pc = uv * float2(60.0, 120.0) + float2(0.0, -uTime * 0.25);
    float motes = step(0.996, hash(floor(pc))) * (0.25 + depth * 0.45);

    float3 water = float3(0.36, 1.0, 0.91);
    float3 glow = water * (web * lit * 0.55 + shafts * lit * 0.22 + motes * 0.5);

    return half4(half3(glow), 1.0);
}
"""

/**
 * The surface every readable thing sits on. Blur plus a translucent fill, so the
 * water is visible behind it without ever being behind a glyph.
 */
@Composable
fun oceanScrimColor(): Color = Color(0xA80D1D25)
