package com.visioo.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.Composable
import com.visioo.app.VisiooApplication
import com.visioo.app.data.AppContainer

/** Creates a ViewModel that gets the app container, without a DI framework. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(crossinline create: (AppContainer) -> VM): VM =
    viewModel(
        factory = viewModelFactory {
            initializer { create((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VisiooApplication).container) }
        },
    )
