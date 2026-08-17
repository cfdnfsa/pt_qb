package com.ptqb.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object AddTorrentRoute
@Serializable data class DetailRoute(val hash: String)

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomePager(
                onOpenAdd = { nav.navigate(AddTorrentRoute) },
                onOpenDetail = { /* 详情已改为 HomePager 内全屏覆盖层 */ },
            )
        }
        composable<AddTorrentRoute> { AddTorrentScreen(onBack = { nav.popBackStack() }) }
    }
}
