package eu.plajta.plajtime

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.icu.util.Calendar
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import eu.plajta.plajtime.ui.theme.PlajTimeTheme
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var bluetoothLeScanner: BluetoothLeScanner? = null  // Change to nullable
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter != null) {
            bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner  // Initialize only if adapter exists
        }

        setContent {
            val bluetoothPermissionGranted = remember { mutableStateOf(false) }
            val isBluetoothEnabled = remember { mutableStateOf(false) }
            val requestPermissionLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                    bluetoothPermissionGranted.value = isGranted
                    if (isGranted) {
                        isBluetoothEnabled.value = checkBluetoothEnabled()
                    }
                }

            LaunchedEffect(Unit) {
                val permissions = listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )

                val missingPermissions = permissions.filter {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                }

                if (missingPermissions.isNotEmpty()) {
                    requestPermissionLauncher.launch(missingPermissions.first()) // Request one at a time
                } else {
                    bluetoothPermissionGranted.value = true
                    isBluetoothEnabled.value = checkBluetoothEnabled()
                }
            }

            val context = LocalContext.current

            PlajTimeTheme {
                Scaffold { innerPadding ->
                    Box(
                        modifier = Modifier.padding(innerPadding).then(Modifier.fillMaxSize())
                    ) {
                        Text(
                            text = "PlajTime",
                            fontSize = 80.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                            textAlign = TextAlign.Center,
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            when {
                                !bluetoothPermissionGranted.value -> {
                                    Text(text = "This app requires Bluetooth access to function.")
                                }
                                !isBluetoothEnabled.value -> {
                                    Text(text = "Ocelíku, Bluetooth je potřeba.")
                                }
                                else -> {
                                    ScanButton(name = "Scan for PlajTime", onClick = { startScan(context) })
                                    Box(
                                        modifier = Modifier.padding(top=5.dp)
                                            .height(40.dp)
                                            .fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ){
                                        if (isScanning) {
                                            ScanningIndicator()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkBluetoothEnabled(): Boolean {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        return bluetoothAdapter?.isEnabled == true
    }

    private fun startScan(context: Context) {
        val scanner = bluetoothLeScanner
        if (isScanning ||
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) ||
            scanner == null) {

            Toast.makeText(context, "Nuh uh", Toast.LENGTH_SHORT).show()
            return
        }
        isScanning = true
        handler.postDelayed({
            isScanning = false
            bluetoothLeScanner?.stopScan(scanCallback)
        }, 10000) // Stop scan after 10 seconds

        val filters = listOf(
            ScanFilter.Builder()
                .setDeviceName("PlajTime")
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothLeScanner?.startScan(filters, settings, scanCallback)
        Toast.makeText(context, "Scanning for PlajTime...", Toast.LENGTH_SHORT).show()
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            bluetoothLeScanner?.stopScan(this)
            isScanning = false

            val device = result?.device
            if (device != null) {
                Toast.makeText(applicationContext, "PlajTime found!", Toast.LENGTH_SHORT).show()
                device.connectGatt(applicationContext, false, gattCallback)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("MainActivity", "Scan failed with error code: $errorCode")
            isScanning = false
            when (errorCode) {
                ScanCallback.SCAN_FAILED_ALREADY_STARTED -> {
                    Log.e("MainActivity", "Scan already started, try stopping it before starting again.")
                }
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> {
                    Log.e("MainActivity", "App registration for scan failed.")
                }
                ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> {
                    Log.e("MainActivity", "Internal error during scan.")
                }
                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> {
                    Log.e("MainActivity", "Bluetooth feature unsupported on this device.")
                }
                else -> {
                    Log.e("MainActivity", "Unknown scan failure occurred.")
                }
            }
            handler.post {
                Toast.makeText(applicationContext, "Scan failed: $errorCode", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.i("GATT", "Connected to GATT server, discovering services...")
                gatt.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.i("GATT", "Disconnected from GATT server")
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("GATT", "Services discovered")
                writeCurrentTime(gatt)
                gatt.close()
            } else {
                Log.e("GATT", "Service discovery failed, status: $status")
            }
        }
    }
}

fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

@SuppressLint("MissingPermission")
private fun writeCurrentTime(gatt: BluetoothGatt) {
    val currentTimeService = gatt.getService(UUID.fromString("00001805-0000-1000-8000-00805f9b34fb"))
    val currentTimeChar = currentTimeService?.getCharacteristic(UUID.fromString("00002A2B-0000-1000-8000-00805f9b34fb"))

    if (currentTimeChar != null) {
        Log.i("GATT", "Current Time Characteristic found!")
        val timeData = getCurrentTimeBytes()
        Log.i("Time", "Please: ${timeData.toHex()}")
        currentTimeChar.setValue(timeData)
        currentTimeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        gatt.writeCharacteristic(currentTimeChar)
    } else {
        Log.e("GATT", "Current Time Characteristic not found")
    }
}

private fun getCurrentTimeBytes(): ByteArray {
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1  // Months are 0-based in Calendar
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val weekday = calendar.get(Calendar.DAY_OF_WEEK)
    val hours = calendar.get(Calendar.HOUR_OF_DAY)
    val minutes = calendar.get(Calendar.MINUTE)
    val seconds = calendar.get(Calendar.SECOND)
    val mseconds = calendar.get(Calendar.MILLISECOND)
    val reason = 0

    return byteArrayOf(
        (year and 0xFF).toByte(),
        ((year shr 8) and 0xFF).toByte(),
        month.toByte(),
        day.toByte(),
        hours.toByte(),
        minutes.toByte(),
        seconds.toByte(),
        weekday.toByte(),
        (mseconds and 0xFF).toByte(),
        reason.toByte()
    )
}
 

@Composable
fun ScanningIndicator(modifier: Modifier = Modifier) {
    Row (modifier=modifier) {
        CircularProgressIndicator()
        Text(
            text="Scanning...",
            modifier = Modifier.padding(start = 10.dp).align(Alignment.CenterVertically)
        )
    }
}

@Composable
fun ScanButton(name: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier) {
        Text(text = name)
    }
}

@Preview(showSystemUi = true)
@Composable
fun GreetingPreview() {
    PlajTimeTheme {
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier.padding(innerPadding).then(Modifier.fillMaxSize()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ScanningIndicator()
                ScanButton(name = "Scan for PlajTime", onClick = { })
            }
        }
    }
}
