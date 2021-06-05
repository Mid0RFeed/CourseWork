package ru.egorxoroshenkov.carinfo

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class DeviceListActivity : AppCompatActivity() {

    val BluetoothSerialUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Debugging
    private val TAG = "DeviceListActivity"
    private val D = true

    // Return Intent extra
    var EXTRA_DEVICE_ADDRESS = "device_address"
    private var newDevicesTitle: TextView? = null

    // Member fields
    private var mBtAdapter: BluetoothAdapter? = null
    private var mPairedDevicesArrayAdapter: ArrayAdapter<String>? = null
    private var mNewDevicesArrayAdapter: ArrayAdapter<String>? = null
    private val _discoveryStartTime: Calendar? = null
    private val _logDateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS")

    // The on-click listener for all devices in the ListViews
    private val mDeviceClickListener =
        OnItemClickListener { av, v, arg2, arg3 -> // Cancel discovery because it's costly and we're about to connect
            mBtAdapter!!.cancelDiscovery()

            // Get the device MAC address, which is the last 17 chars in the View
            val info = (v as TextView).text.toString()
            val address = info.substring(info.length - 17)

            // Create the result Intent and include the MAC address
            val intent = Intent()
            intent.putExtra(EXTRA_DEVICE_ADDRESS, address)

            // Set result and finish this Activity
            setResult(RESULT_OK, intent)
            finish()
        }

    // The BroadcastReceiver that listens for discovered devices and
    // changes the title when discovery is finished
    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND == action) {
                // Get the BluetoothDevice object from the Intent
                val device =
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                // If it's already paired, skip it, because it's been listed already
                if (device!!.bondState != BluetoothDevice.BOND_BONDED) {
                    mNewDevicesArrayAdapter!!.add(
                        """
                        ${device.name}
                        ${device.address}
                        """.trimIndent()
                    )
                }
                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                setProgressBarIndeterminateVisibility(false)
//                setTitle(R.string.select_device)

                /*for (BluetoothDevice device : mBtAdapter.getBondedDevices())
                {
                    mNewDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                }*/

                // Trawl through the logs to find any devices that were skipped >:(
                try {
                    val process = Runtime.getRuntime().exec("logcat -d -v time *:E")
                    val bufferedReader = BufferedReader(
                        InputStreamReader(process.inputStream)
                    )
                    var line: String?
                    val pattern = Pattern.compile("(.{18}).*\\[(.+)\\] class is 0x00 - skip it.")
                    while (bufferedReader.readLine().also { line = it } != null) {
                        val matcher = pattern.matcher(line)
                        if (matcher.find()) {
                            // Found a blocked device, check if it was newly discovered.
                            // Android log timestamps don't contain the year!?
                            val logTimeStamp = Integer.toString(
                                _discoveryStartTime!![Calendar.YEAR]
                            ) + "-" + matcher.group(1)
                            var logTime: Date? = null
                            try {
                                logTime = _logDateFormat.parse(logTimeStamp)
                            } catch (e: ParseException) {
                            }
                            if (logTime != null) {
                                if (logTime.after(_discoveryStartTime.time)) {
                                    // Device was discovered during this scan,
                                    // now we want to get the name of the device.
                                    val deviceAddress = matcher.group(2)
                                    val device = mBtAdapter!!.getRemoteDevice(deviceAddress)

                                    // In order to get the name, we must attempt to connect to the device.
                                    // This will attempt to pair with the device, and will ask the user
                                    // for a PIN code if one is required.
                                    try {
                                        val socket = device.createRfcommSocketToServiceRecord(
                                            BluetoothSerialUuid
                                        )
                                        socket.connect()
                                        socket.close()
                                        mNewDevicesArrayAdapter!!.add(
                                            """
                                            ${device.name}
                                            ${device.address}
                                            """.trimIndent()
                                        )
                                    } catch (e: IOException) {
                                    }
                                }
                            }
                        }
                    }
                } catch (e: IOException) {
                }
                if (mNewDevicesArrayAdapter!!.count != 0) {
                    newDevicesTitle?.setText(R.string.title_other_devices)
                } else {
                    newDevicesTitle?.setText(R.string.none_found)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup the window
        //requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_device_list)
        newDevicesTitle = findViewById<View>(R.id.title_new_devices) as TextView

        // Set result CANCELED in case the user backs out
        setResult(RESULT_CANCELED)

        // Initialize the button to perform device discovery
        val scanButton = findViewById<View>(R.id.button_scan) as TextView
        scanButton.setOnClickListener { v ->
            doDiscovery()
            v.visibility = View.GONE
        }

        // Initialize array adapters. One for already paired devices and
        // one for newly discovered devices
        mPairedDevicesArrayAdapter = ArrayAdapter(this, R.layout.device_name)
        mNewDevicesArrayAdapter = ArrayAdapter(this, R.layout.device_name)

        // Find and set up the ListView for paired devices
        val pairedListView = findViewById<View>(R.id.paired_devices) as ListView
        pairedListView.adapter = mPairedDevicesArrayAdapter
        pairedListView.onItemClickListener = mDeviceClickListener

        // Find and set up the ListView for newly discovered devices
        val newDevicesListView = findViewById<View>(R.id.new_devices) as ListView
        newDevicesListView.adapter = mNewDevicesArrayAdapter
        newDevicesListView.onItemClickListener = mDeviceClickListener

        // Register for broadcasts when a device is discovered
        var filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        this.registerReceiver(mReceiver, filter)

        // Register for broadcasts when discovery has finished
        filter = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        this.registerReceiver(mReceiver, filter)

        // Get the local Bluetooth adapter
        mBtAdapter = BluetoothAdapter.getDefaultAdapter()

        // Get a set of currently paired devices
        val pairedDevices = mBtAdapter!!.bondedDevices

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size > 0) {
            findViewById<View>(R.id.title_paired_devices).visibility = View.VISIBLE
            for (device in pairedDevices) {
                mPairedDevicesArrayAdapter!!.add(
                    """
                    ${device.name}
                    ${device.address}
                    """.trimIndent()
                )
            }
        } else {
            val noDevices = resources.getText(R.string.none_paired).toString()
            mPairedDevicesArrayAdapter!!.add(noDevices)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Make sure we're not doing discovery anymore
        if (mBtAdapter != null) {
            mBtAdapter!!.cancelDiscovery()
        }

        // Unregister broadcast listeners
        unregisterReceiver(mReceiver)
    }

    /**
     * Start device discover with the BluetoothAdapter
     */
    private fun doDiscovery() {
        if (D) Log.d(TAG, "doDiscovery()")

        // Indicate scanning in the title
        setProgressBarIndeterminateVisibility(true)
        setTitle(R.string.scanning)

        // Turn on sub-title for new devices
        newDevicesTitle!!.visibility = View.VISIBLE
        newDevicesTitle?.setText(R.string.scanning)

        // If we're already discovering, stop it
        if (mBtAdapter!!.isDiscovering) {
            mBtAdapter!!.cancelDiscovery()
        }

        // Request discover from BluetoothAdapter
        mBtAdapter!!.startDiscovery()
    }

}