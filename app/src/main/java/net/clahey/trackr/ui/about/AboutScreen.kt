package net.clahey.trackr.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.clahey.trackr.R
import net.clahey.trackr.ui.rememberStarterCategoryInputs

// @spec APP-UI-010, CAT-UI-090
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val starterInputs = rememberStarterCategoryInputs()
    val addedCount by viewModel.addedCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }

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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.about_body_fast), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_body_local), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_body_account), style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(4.dp))
            Button(onClick = { viewModel.addStarterCategories(starterInputs) }) {
                Text(stringResource(R.string.action_add_starter_categories))
            }
            TextButton(onClick = { uriHandler.openUri(SOURCE_URL) }) {
                Text(stringResource(R.string.about_source_code))
            }

            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.about_version, version),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val SOURCE_URL = "https://github.com/clahey/trackr"
