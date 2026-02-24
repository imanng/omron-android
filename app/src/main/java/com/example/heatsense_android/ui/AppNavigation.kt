package com.example.heatsense_android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.heatsense_android.ble.ScannedDevice

const val ROUTE_DEVICES = "devices"
const val ROUTE_SENSOR = "sensor/{address}"

fun sensorRoute(address: String) = "sensor/$address"

@Composable
fun AppNavigation(viewModel: SensorViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ROUTE_DEVICES
    ) {
        composable(ROUTE_DEVICES) {
            DeviceListScreen(
                viewModel = viewModel,
                onDeviceSelected = { device: ScannedDevice ->
                    viewModel.stopScan()
                    viewModel.connect(device)
                    navController.navigate(sensorRoute(device.address))
                }
            )
        }
        composable(
            route = ROUTE_SENSOR,
            arguments = listOf(navArgument("address") { type = NavType.StringType })
        ) { backStackEntry ->
            val address = backStackEntry.arguments?.getString("address") ?: ""
            DisposableEffect(Unit) {
                onDispose {
                    viewModel.disconnect()
                }
            }
            SensorDetailScreen(
                deviceAddress = address,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
