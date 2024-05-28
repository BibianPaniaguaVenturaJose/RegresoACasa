package com.example.regresoacasa.Mapas


import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.regresoacasa.Api.ApiService
import com.example.regresoacasa.Rutas.RouteResponse
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


@SuppressLint("MissingPermission")
@Composable
fun CurrentLocationScreen() {
    val context = LocalContext.current
    var hasLocationPermission by remember { mutableStateOf(false) }

    // Launcher to request location permission
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    // Check if we already have the permission
    LaunchedEffect(Unit) {
        hasLocationPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) {
            // Request location permissions
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // UI content
    if (hasLocationPermission) {
        Text("Permisos de Ubicacion otorgados")
        CurrentLocationContent(usePreciseLocation = true)
    } else {
        Text("Permisos de Ubicacion necesarios")
    }

}
@RequiresPermission(
    anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION],
)
@Composable
fun CurrentLocationContent(usePreciseLocation: Boolean) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val locationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // Ubicación de mi casa
    var myHome = remember { LatLng(20.066555,-101.062528) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(myHome, 10f)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val markerState = rememberMarkerState(position = myHome)

        Box(Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ) {
                // Marcador en la casa
                Marker(
                    state = MarkerState(position = myHome),
                    title = "Casa",
                    snippet = "Marker in home"
                )

                // Si la posición del marcador es diferente a la ubicación actual, muestra el marcador en la ubicación actual y crea la ruta
                if (!markerState.position.equals(myHome)) {
                    Marker(
                        state = MarkerState(position = markerState.position),
                        title = "Ubicacion actual",
                        snippet = "Marker in current position"
                    )
                    val RouteList = remember { mutableStateOf<List<LatLng>>(emptyList()) }
                    createRoute(myHome, markerState.position) { routePoints ->
                        val pointsList = mutableListOf<LatLng>()
                        for (i in routePoints.indices step 2) {
                            val lat = routePoints[i]
                            val lng = routePoints[i + 1]
                            pointsList.add(LatLng(lat, lng))
                        }
                        RouteList.value = pointsList
                    }
                    // Dibuja la ruta
                    Polyline(points = RouteList.value)
                }
            }
            // Botón en la parte inferior
            Column(
                modifier = Modifier.align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        // Obtiene la ubicación actual del dispositivo
                        scope.launch(Dispatchers.IO) {
                            val priority = if (usePreciseLocation) {
                                Priority.PRIORITY_HIGH_ACCURACY
                            } else {
                                Priority.PRIORITY_BALANCED_POWER_ACCURACY
                            }
                            val result = locationClient.getCurrentLocation(
                                priority,
                                CancellationTokenSource().token,
                            ).await()
                            result?.let { fetchedLocation ->
                                markerState.position =
                                    LatLng(fetchedLocation.latitude, fetchedLocation.longitude)
                            }
                        }
                    },
                ) {
                    Text(text = "Dibujar Ruta de Regreso")
                }
            }
        }
    }
}

// Crea la ruta entre dos ubicaciones
private fun createRoute(
    startLocation: LatLng,
    endLocation: LatLng,
    callback: (List<Double>) -> Unit
) {
    val routePoints = mutableListOf<LatLng>()
    CoroutineScope(Dispatchers.IO).launch {
        val call = getRetrofit().create(ApiService::class.java)
            .getRoute(
                "5b3ce3597851110001cf624834b48390e0d94b8abd9033d7d4acdb4f",
                "${startLocation.longitude},${startLocation.latitude}",
                "${endLocation.longitude},${endLocation.latitude}"
            )
        if (call.isSuccessful) {
            drawRoute(call.body(), routePoints)
            val pointsList = routePoints.flatMap { listOf(it.latitude, it.longitude) }
            callback(pointsList)
        } else {
            Log.i("ruta","OK")
        }
    }
}

// Dibuja la ruta obtenida de la API de rutas
private fun drawRoute(routeResponse: RouteResponse?, routePoints: MutableList<LatLng>) {
    routeResponse?.features?.firstOrNull()?.geometry?.coordinates?.forEach {
        val latLng = LatLng(it[1], it[0])
        routePoints.add(latLng)
    }
}

// Obtiene la instancia de Retrofit para realizar llamadas a la API de rutas
private fun getRetrofit(): Retrofit {
    return Retrofit.Builder()
        .baseUrl("https://api.openrouteservice.org/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}
