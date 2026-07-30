package net.clahey.trackr.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.NoAccounts
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.clahey.trackr.R
import net.clahey.trackr.ui.resolveStarterCategoryInputs

// Brand palette — canonical definition in docs/brand.md (update there; `rg docs/brand.md` finds every hardcoded copy).
private val BrandLightBlue = Color(0xFF47AADC)   // light blue
private val BrandDarkBlue = Color(0xFF04325C)    // dark blue
private val BrandYellow = Color(0xFFFCD214)      // yellow
private val BrandDarkYellow = Color(0xFFEBC413)  // brand yellow at V=0.92 (same hue/sat), for light surfaces
private val BrandGreen = Color(0xFF148244)       // green

// Hero banner gradient endpoints.
private val GradientTop = BrandLightBlue
private val GradientBottom = BrandDarkBlue

// About point-icon colors — from the brand palette (docs/brand.md). "Log fast"
// flips per mode (see AboutScreen): bright brand yellow on dark, the darker same-hue yellow on light
// (bright yellow vanishes on white). On-device stays light blue (dark blue read as black on white);
// no-account stays green — both legible in both modes.
private val PointLocal = BrandLightBlue   // device
private val PointAccount = BrandGreen     // privacy/positive
private val PointFastDark = BrandYellow
private val PointFastLight = BrandDarkYellow

// @spec APP-UI-010, CAT-UI-090
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val addedCount by viewModel.addedCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }

    // Bright brand yellow reads on dark; the darker same-hue yellow is needed against a light surface.
    val pointFast = if (isSystemInDarkTheme()) PointFastDark else PointFastLight


    // @spec CAT-UI-090
    LaunchedEffect(addedCount) {
        val n = addedCount ?: return@LaunchedEffect
        val msg = if (n > 0) context.resources.getQuantityString(R.plurals.starters_added, n, n)
        else context.getString(R.string.about_starters_all_present)
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeAddedCount()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            BrandHero()

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AboutPoint(
                    icon = Icons.Outlined.Bolt,
                    tint = pointFast,
                    title = stringResource(R.string.about_point_fast_title),
                    body = stringResource(R.string.about_point_fast_body),
                )
                AboutPoint(
                    icon = Icons.Outlined.CloudOff,
                    tint = PointLocal,
                    title = stringResource(R.string.about_point_local_title),
                    body = stringResource(R.string.about_point_local_body),
                )
                AboutPoint(
                    icon = Icons.Outlined.NoAccounts,
                    tint = PointAccount,
                    title = stringResource(R.string.about_point_account_title),
                    body = stringResource(R.string.about_point_account_body),
                )

                Spacer(Modifier.height(4.dp))
                Button(onClick = { viewModel.addStarterCategories(resolveStarterCategoryInputs(context)) }) {
                    Text(stringResource(R.string.action_add_starter_categories))
                }
                // Grouped so the parent's 16dp spacing doesn't sit between the two links.
                Column {
                    TextButton(
                        onClick = { uriHandler.openUri(SOURCE_URL) },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(stringResource(R.string.about_source_code))
                    }
                    TextButton(
                        onClick = { uriHandler.openUri(ISSUES_URL) },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(stringResource(R.string.about_feedback))
                    }
                }

                Text(
                    stringResource(R.string.about_version, version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BrandHero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(GradientTop, GradientBottom),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                )
            )
            // Slightly less top than bottom padding so the block sits a touch higher — balancing the
            // block's center against the wordmark's (the thin logo top is lighter than the slogan).
            .padding(start = 16.dp, end = 16.dp, top = 30.dp, bottom = 42.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The launcher foreground has a large transparent safe-zone (art occupies ~27..66dp of a
            // 96dp render). Trim it to the artwork so the mark sits close to the wordmark. requiredSize
            // renders full-size and overflows so clipToBounds actually crops (plain size() would be
            // constrained by the box height and scale the vector down instead).
            Box(
                modifier = Modifier.width(96.dp).height(46.dp).clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.requiredSize(96.dp),
                )
            }
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Row {
                Text(
                    stringResource(R.string.about_hero_slogan_lead),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.about_hero_slogan_accent),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandYellow,
                )
            }
        }
    }
}

@Composable
private fun AboutPoint(icon: ImageVector, tint: Color, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            // Top-aligned, nudged down 3dp so the glyph top sits at the title's cap rather than above it
            // (the row top aligns to the text line box, whose ascent sits above the cap height).
            modifier = Modifier.padding(top = 3.dp).size(28.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val SOURCE_URL = "https://github.com/clahey/trackr"
private const val ISSUES_URL = "https://github.com/clahey/trackr/issues"
