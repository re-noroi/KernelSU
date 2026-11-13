package me.weishu.kernelsu.ui.screen.tuning

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun TuningDocsScreen() {
    when (LocalUiMode.current) {
        UiMode.Miuix -> TuningDocsScreenMiuix()
        UiMode.Material, UiMode.Minimal -> TuningDocsScreenMaterial()
    }
}

// ---------------------------------------------------------------------------
// Content
// ---------------------------------------------------------------------------

private data class DocItem(
    val name: String,
    val what: String,
    val values: String,
    val recommended: String,
)

private data class DocSection(
    val title: String,
    val items: List<DocItem>,
)

private val tuningIntro = listOf(
    "Changes apply immediately. If you want them to survive a reboot, turn on \"Persist on boot\" first.",
    "Change one thing at a time and use the device normally for a while before changing more.",
    "If anything feels worse, set it back to the recommended value listed below. Nothing here can permanently damage the device.",
)

private val tuningSections = listOf(
    DocSection(
        title = "Thermals and Battery",
        items = listOf(
            DocItem(
                name = "Thermal profile",
                what = "Picks the temperature limit table the kernel uses to throttle the CPU and GPU.",
                values = "The profiles shown come from your kernel (for example default, performance or battery).",
                recommended = "Keep the default profile. Choose a cooler profile if the phone gets too hot; expect lower performance.",
            ),
            DocItem(
                name = "Bypass charging",
                what = "Runs the phone from the charger and stops charging the battery. Less charging means less heat while gaming.",
                values = "On or off.",
                recommended = "On while gaming or using heavy apps for a long time. Off for normal use so the battery still charges.",
            ),
            DocItem(
                name = "Max charging current",
                what = "Sets the highest current the charger is allowed to use. The phone can still charge slower on its own.",
                values = "1.0 A to 10.0 A in 0.5 A steps.",
                recommended = "Leave it at the default. Lower it to about 2.0-3.0 A for cooler overnight charging. Higher is not always faster.",
            ),
        ),
    ),
    DocSection(
        title = "Memory Management",
        items = listOf(
            DocItem(
                name = "Swappiness",
                what = "How eagerly the kernel moves app memory into zRAM/swap to free physical RAM.",
                values = "0 (almost never swap) to 100 (swap very eagerly). Default is 60.",
                recommended = "60. On 8 GB or more you can try 40-60. Do not set 0, because low-memory apps may be killed.",
            ),
            DocItem(
                name = "VFS cache pressure",
                what = "How quickly the kernel throws away cached file data when it needs RAM.",
                values = "0 (keep cache as long as possible) to 1000 (drop cache very quickly). Default is 100.",
                recommended = "100. Try 50 for more file caching, or 200 only if you are often low on RAM.",
            ),
            DocItem(
                name = "kcompressd",
                what = "A kernel helper that compresses memory in the background, so the main reclaim thread (kswapd) is not overloaded. This is a custom kernel feature.",
                values = "0 turns the helper off. 1-256 is the queue length before kswapd compresses a page itself. Default is 24.",
                recommended = "24 (default). 16-32 is a safe range. Higher values batch more work asynchronously (less CPU overhead, slightly delayed compression); lower values compress sooner but keep kswapd busier.",
            ),
            DocItem(
                name = "MGLRU",
                what = "A newer memory reclaim method (multi-generational LRU) that usually keeps the system smoother when RAM is full.",
                values = "On or off.",
                recommended = "On if your kernel supports it. Turn it off only if you notice worse battery life.",
            ),
            DocItem(
                name = "MGLRU min_ttl_ms",
                what = "How long a page must stay unused before MGLRU is allowed to reclaim it.",
                values = "0 to 10000 milliseconds. Default is 1000 (1 second).",
                recommended = "1000. 500-2000 is safe. Higher keeps apps cached longer but uses more RAM.",
            ),
        ),
    ),
    DocSection(
        title = "zRAM",
        items = listOf(
            DocItem(
                name = "Size",
                what = "How much compressed RAM is reserved for zRAM, which acts as swap. Bigger zRAM keeps more apps alive in the background.",
                values = "Auto (kernel/init default), 2, 3, 4, 6 or 8 GB.",
                recommended = "Auto. On a 6-8 GB phone, 4 GB is a good manual choice. Use 6 GB only if you multitask heavily; too much wastes RAM.",
            ),
            DocItem(
                name = "Compression algorithm",
                what = "The method used to squeeze data before it goes into zRAM. Faster algorithms use less CPU, stronger ones save more RAM.",
                values = "The algorithms your kernel offers, such as lz4, lzo-rle or zstd.",
                recommended = "Keep the default. lz4 is the smoothest and uses the least power; zstd saves the most RAM but costs more CPU.",
            ),
        ),
    ),
    DocSection(
        title = "Schedulers - FakeDreamers",
        items = listOf(
            DocItem(
                name = "Pelt multiplier tweak",
                what = "Makes the CPU scheduler notice load faster, so it raises frequency sooner. It is a custom kernel feature.",
                values = "On or off. Off is the kernel default.",
                recommended = "Off for daily use. Turn it on only while gaming; it can cause severe overheating and battery drain on some devices.",
            ),
            DocItem(
                name = "DVFS headroom",
                what = "Adds a small frequency safety margin ahead of rising load, so the CPU does not lag behind a sudden burst of work. Custom kernel feature.",
                values = "Enabled (default) or disabled. Disabled removes the extra margin.",
                recommended = "Enabled. Disable it only if you want the last bit of battery savings and accept possible stutter.",
            ),
            DocItem(
                name = "DVFS headroom scaling",
                what = "Turns on your own percentage adjustments for the headroom of each core group. When off, the kernel uses its normal headroom.",
                values = "On or off. Off by default.",
                recommended = "Off. Turn it on only if you want to fine-tune with the three sliders below.",
            ),
            DocItem(
                name = "Little / Perf / Prime core scaling",
                what = "Extra headroom percentage for each core group. Positive values add more frequency margin (snappier, hotter); negative values remove margin (cooler, may stutter).",
                values = "Little and Perf: -50 to 100. Prime: -50 to 30. Defaults are 20 (Little), 10 (Perf) and 2 (Prime).",
                recommended = "Keep the defaults 20 / 10 / 2. Raise Little slightly for smoother scrolling. Keep Prime low because it is the hottest core.",
            ),
        ),
    ),
    DocSection(
        title = "Schedulers - CGroup",
        items = listOf(
            DocItem(
                name = "UClamp min",
                what = "The lowest performance level (0-100% of a core's capacity) a task group is guaranteed. It sets a frequency floor.",
                values = "0 to 100. 0 means no minimum.",
                recommended = "0 for Background. Leave Top App and Foreground at their defaults; raising everything makes the phone hot.",
            ),
            DocItem(
                name = "UClamp max",
                what = "The highest performance level a task group may use. Lowering it leaves more CPU headroom for other groups.",
                values = "0 to 100. 100 means no cap.",
                recommended = "100 for Top App and Foreground. 40-60 for Background if you want foreground apps to stay smooth.",
            ),
            DocItem(
                name = "CPU shares",
                what = "The relative amount of CPU time a group gets when the CPU is busy.",
                values = "5% to 100%. Higher means more CPU time for that group.",
                recommended = "Leave the default. Raise Top App if your foreground app stutters, or lower Background to favour it.",
            ),
        ),
    ),
    DocSection(
        title = "Networking",
        items = listOf(
            DocItem(
                name = "TCP congestion algorithm",
                what = "How the kernel reacts when the network is congested. Different algorithms trade speed against fairness.",
                values = "The algorithms your kernel offers, such as cubic, bbr or westwood.",
                recommended = "cubic (default) is safest. bbr can improve speed and latency on mobile data; switch back if downloads behave oddly.",
            ),
            DocItem(
                name = "Queueing discipline",
                what = "How outgoing packets are queued before they are sent. It affects latency when you upload or download.",
                values = "fq_codel (balanced default) or cake (better fairness and bufferbloat control, slightly more CPU).",
                recommended = "fq_codel. Try cake if calls or games lag while the network is busy.",
            ),
        ),
    ),
    DocSection(
        title = "Display and Extras",
        items = listOf(
            DocItem(
                name = "Color calibration",
                what = "Adjusts the screen's colour output through KCAL: red, green and blue levels, saturation, value, contrast and hue.",
                values = "RGB 0-256. Saturation, value and contrast 128-383. Hue 0-1536. Defaults: RGB 256, saturation/value/contrast 255, hue 0.",
                recommended = "Keep the defaults unless the screen looks too warm, too cool or washed out. Make small changes and compare with a white background.",
            ),
            DocItem(
                name = "Pixel density",
                what = "Changes the UI scale by overriding the screen density (dots per inch). Higher values fit more on screen with smaller elements.",
                values = "240 to 1200 DPI. The physical value shown is your screen's real density.",
                recommended = "Use the physical/default value. 400-480 is common on this class of phone. Very high values can break layouts; press Reset if that happens.",
            ),
        ),
    ),
)

@Composable
private fun DocItemContent(
    item: DocItem,
    titleColor: Color,
    bodyColor: Color,
    accentColor: Color,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            text = item.name,
            color = titleColor,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.what,
            color = bodyColor,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Values: ${item.values}",
            color = bodyColor,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Recommended: ${item.recommended}",
            color = accentColor,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---------------------------------------------------------------------------
// Material
// ---------------------------------------------------------------------------

@Composable
private fun TuningDocsScreenMaterial() {
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val layoutDirection = LocalLayoutDirection.current
    val isMinimal = LocalUiMode.current == UiMode.Minimal

    ExpressiveScaffold(
        containerColor = if (isMinimal) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.tuning_docs_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                TonalCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 13.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "How to use this guide",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        tuningIntro.forEach { line ->
                            Text(
                                text = "- $line",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                }
            }

            tuningSections.forEach { section ->
                item { DocsSectionHeaderMaterial(section.title) }
                item {
                    TonalCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column {
                            section.items.forEachIndexed { index, docItem ->
                                DocItemContent(
                                    item = docItem,
                                    titleColor = MaterialTheme.colorScheme.onSurface,
                                    bodyColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    accentColor = MaterialTheme.colorScheme.primary,
                                )
                                if (index != section.items.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "kcompressd, the PELT multiplier tweak and DVFS headroom are custom features of this kernel. Everything can be reverted by choosing the recommended value or by turning off \"Persist on boot\" and rebooting.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun DocsSectionHeaderMaterial(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp),
    )
}

// ---------------------------------------------------------------------------
// Miuix
// ---------------------------------------------------------------------------

@Composable
private fun TuningDocsScreenMiuix() {
    val navigator = LocalNavigator.current
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current

    MiuixScaffold(
        topBar = {
            BlurredBar(backdrop) {
                MiuixTopAppBar(
                    color = barColor,
                    title = stringResource(R.string.tuning_docs_title),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        MiuixIconButton(onClick = { navigator.pop() }) {
                            MiuixIcon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                            )
                        }
                    },
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(
            modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    bottom = innerPadding.calculateBottomPadding() +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
                ),
                overscrollEffect = null,
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "How to use this guide",
                                color = colorScheme.onBackground,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(6.dp))
                            tuningIntro.forEach { line ->
                                Text(
                                    text = "- $line",
                                    color = colorScheme.onSurfaceVariantSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                    }
                }

                tuningSections.forEach { section ->
                    item {
                        Text(
                            text = section.title,
                            color = colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                section.items.forEachIndexed { index, docItem ->
                                    DocItemContent(
                                        item = docItem,
                                        titleColor = colorScheme.onBackground,
                                        bodyColor = colorScheme.onSurfaceVariantSummary,
                                        accentColor = colorScheme.primary,
                                    )
                                    if (index != section.items.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = colorScheme.outline.copy(alpha = 0.4f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "kcompressd, the PELT multiplier tweak and DVFS headroom are custom features of this kernel. Everything can be reverted by choosing the recommended value or by turning off \"Persist on boot\" and rebooting.",
                        color = colorScheme.onSurfaceVariantSummary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
