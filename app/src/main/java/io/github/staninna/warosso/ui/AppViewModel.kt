package io.github.staninna.warosso.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.staninna.warosso.AppContainer
import io.github.staninna.warosso.WarOssoApp

/** The screen's view model, built from the app's [AppContainer]. This is all the DI there is. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as WarOssoApp).container
    return viewModel { create(container) }
}
