package com.ptqb.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable

@Serializable object TorrentsRoute
@Serializable object ServersRoute
@Serializable object AddTorrentRoute

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = TorrentsRoute) {
        composable<TorrentsRoute> {
            PlaceholderScreen("种子列表（待实现）")
        }
        composable<ServersRoute> {
            PlaceholderScreen("服务器（待实现）")
        }
        composable<AddTorrentRoute> {
            PlaceholderScreen("添加种子（待实现）")
        }
    }
}

@Composable
private fun PlaceholderScreen(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text)
    }
}
