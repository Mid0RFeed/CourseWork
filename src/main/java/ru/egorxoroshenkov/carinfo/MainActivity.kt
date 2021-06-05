package ru.egorxoroshenkov.carinfo

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageManager
import android.location.Location
import android.os.*
import android.os.StrictMode.ThreadPolicy
import android.util.DisplayMetrics
import android.util.Log
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.romainpiel.shimmer.Shimmer
import kotlinx.android.synthetic.main.activity_main.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.experimental.and


class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    val MESSAGE_STATE_CHANGE = 1

    /*0	Automatic protocol detection
   1	SAE J1850 PWM (41.6 kbaud)
   2	SAE J1850 VPW (10.4 kbaud)
   3	ISO 9141-2 (5 baud init, 10.4 kbaud)
   4	ISO 14230-4 KWP (5 baud init, 10.4 kbaud)
   5	ISO 14230-4 KWP (fast init, 10.4 kbaud)
   6	ISO 15765-4 CAN (11 bit ID, 500 kbaud)
   7	ISO 15765-4 CAN (29 bit ID, 500 kbaud)
   8	ISO 15765-4 CAN (11 bit ID, 250 kbaud) - used mainly on utility vehicles and Volvo
   9	ISO 15765-4 CAN (29 bit ID, 250 kbaud) - used mainly on utility vehicles and Volvo


    01 04 - ENGINE_LOAD
    01 05 - ENGINE_COOLANT_TEMPERATURE
    01 0C - ENGINE_RPM
    01 0D - VEHICLE_SPEED
    01 0F - INTAKE_AIR_TEMPERATURE
    01 10 - MASS_AIR_FLOW
    01 11 - THROTTLE_POSITION_PERCENTAGE
    01 1F - ENGINE_RUN_TIME
    01 2F - FUEL_LEVEL
    01 46 - AMBIENT_AIR_TEMPERATURE
    01 51 - FUEL_TYPE
    01 5E - FUEL_CONSUMPTION_1
    01 5F - FUEL_CONSUMPTION_2

   */

    /*0	Automatic protocol detection
   1	SAE J1850 PWM (41.6 kbaud)
   2	SAE J1850 VPW (10.4 kbaud)
   3	ISO 9141-2 (5 baud init, 10.4 kbaud)
   4	ISO 14230-4 KWP (5 baud init, 10.4 kbaud)
   5	ISO 14230-4 KWP (fast init, 10.4 kbaud)
   6	ISO 15765-4 CAN (11 bit ID, 500 kbaud)
   7	ISO 15765-4 CAN (29 bit ID, 500 kbaud)
   8	ISO 15765-4 CAN (11 bit ID, 250 kbaud) - used mainly on utility vehicles and Volvo
   9	ISO 15765-4 CAN (29 bit ID, 250 kbaud) - used mainly on utility vehicles and Volvo


    01 04 - ENGINE_LOAD
    01 05 - ENGINE_COOLANT_TEMPERATURE
    01 0C - ENGINE_RPM
    01 0D - VEHICLE_SPEED
    01 0F - INTAKE_AIR_TEMPERATURE
    01 10 - MASS_AIR_FLOW
    01 11 - THROTTLE_POSITION_PERCENTAGE
    01 1F - ENGINE_RUN_TIME
    01 2F - FUEL_LEVEL
    01 46 - AMBIENT_AIR_TEMPERATURE
    01 51 - FUEL_TYPE
    01 5E - FUEL_CONSUMPTION_1
    01 5F - FUEL_CONSUMPTION_2

   */
    val MESSAGE_READ = 2
    val MESSAGE_WRITE = 3
    val MESSAGE_DEVICE_NAME = 4
    val MESSAGE_TOAST = 5

    // Key names received from the BluetoothChatService Handler
    val DEVICE_NAME = "device_name"
    val TOAST = "toast"

    protected val dtcLetters = charArrayOf('P', 'C', 'B', 'U')
    protected val hexArray = "0123456789ABCDEF".toCharArray()

    private val PIDS = arrayOf(
        "01", "02", "03", "04", "05", "06", "07", "08",
        "09", "0A", "0B", "0C", "0D", "0E", "0F", "10",
        "11", "12", "13", "14", "15", "16", "17", "18",
        "19", "1A", "1B", "1C", "1D", "1E", "1F", "20"
    )

    // Intent request codes
    private val REQUEST_CONNECT_DEVICE = 2
    private val REQUEST_ENABLE_BT = 3
    private val APPBAR_ELEVATION = 14f
    private val actionbar = true
    val commandslist: ArrayList<String> = ArrayList()
    val avgconsumption: ArrayList<Double> = ArrayList()
    val troubleCodesArray: ArrayList<String> = ArrayList()
    var itemtemp: MenuItem? = null

    var currentdevice: BluetoothDevice? = null

    var commandmode =
        false
    var initialized: Boolean = false
    var m_getPids: Boolean = false
    var tryconnect: Boolean = false
    var defaultStart: Boolean = false
    var devicename: String? = null
    var deviceprotocol: String? = null

    var initializeCommands = ArrayList<String>()
    var serverIntent: Intent? = null
    var troubleCodes: TroubleCodes? = null
    var VOLTAGE = "ATRV"
    var PROTOCOL = "ATDP"
    var RESET = "ATZ"
    var PIDS_SUPPORTED20 = "0100"
    var ENGINE_COOLANT_TEMP = "0105"  //A-40

    //A-40
    var ENGINE_RPM = "010C"  //((A*256)+B)/4

    //((A*256)+B)/4
    var ENGINE_LOAD = "0104"  // A*100/255

    // A*100/255
    var VEHICLE_SPEED = "010D"  //A

    //A
    var INTAKE_AIR_TEMP = "010F"  //A-40

    //A-40
    var MAF_AIR_FLOW = "0110" //MAF air flow rate 0 - 655.35	grams/sec ((256*A)+B) / 100  [g/s]

    //MAF air flow rate 0 - 655.35	grams/sec ((256*A)+B) / 100  [g/s]
    var ENGINE_OIL_TEMP = "015C"  //A-40

    //A-40
    var FUEL_RAIL_PRESSURE = "0122" // ((A*256)+B)*0.079

    // ((A*256)+B)*0.079
    var INTAKE_MAN_PRESSURE = "010B" //Intake manifold absolute pressure 0 - 255 kPa

    //Intake manifold absolute pressure 0 - 255 kPa
    var CONT_MODULE_VOLT = "0142"  //((A*256)+B)/1000

    //((A*256)+B)/1000
    var AMBIENT_AIR_TEMP = "0146"  //A-40

    //A-40
    var CATALYST_TEMP_B1S1 = "013C"  //(((A*256)+B)/10)-40

    //(((A*256)+B)/10)-40
    var STATUS_DTC = "0101" //Status since DTC Cleared

    //Status since DTC Cleared
    var THROTTLE_POSITION = "0111" //Throttle position 0 -100 % A*100/255

    //Throttle position 0 -100 % A*100/255
    var OBD_STANDARDS = "011C" //OBD standards this vehicle

    //OBD standards this vehicle
    var PIDS_SUPPORTED = "0120" //PIDs supported

    private var mConnectedDeviceName = "Ecu"
    private var rpmval = 0
    private var intakeairtemp: Int = 0
    private var ambientairtemp: Int = 0
    private var coolantTemp: Int = 0
    private var mMaf: Int = 0
    private var engineoiltemp = 0
    private var b1s1temp: Int = 0
    private var Enginetype: Int = 0
    private var FaceColor: Int = 0
    private var whichCommand = 0
    private var m_dedectPids: Int = 0
    private var connectcount: Int = 0
    private var trycount: Int = 0
    private var mEnginedisplacement = 1500

    // Local Bluetooth adapter
    private var mBluetoothAdapter: BluetoothAdapter? = null

    // Member object for the chat services
    private var mBtService: BluetoothService? = null

    var inStream = StringBuilder()

    // The Handler that gets information back from the BluetoothChatService
    // Array adapter for the conversation thread
//    private val mConversationArrayAdapter: ArrayAdapter<String>? = null

    lateinit var shimmer: Shimmer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)


        //ATZ reset all
        //ATDP Describe the current Protocol
        //ATAT0-1-2 Adaptive Timing Off - daptive Timing Auto1 - daptive Timing Auto2
        //ATE0-1 Echo Off - Echo On
        //ATSP0 Set Protocol to Auto and save it
        //ATMA Monitor All
        //ATL1-0 Linefeeds On - Linefeeds Off
        //ATH1-0 Headers On - Headers Off
        //ATS1-0 printing of Spaces On - printing of Spaces Off
        //ATAL Allow Long (>7 byte) messages
        //ATRD Read the stored data
        //ATSTFF Set time out to maximum
        //ATSTHH Set timeout to 4ms
        initializeCommands.addAll(
            arrayOf(
                "ATL0",
                "ATE1",
                "ATH1",
                "ATAT1",
                "ATSTFF",
                "ATI",
                "ATDP",
                "ATSP0",
                "0100"
            )
        )

        ///// Checking if Device has Bluetooth or not
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (mBluetoothAdapter == null) {
            Toast.makeText(applicationContext, "Bluetooth is not available", Toast.LENGTH_LONG)
                .show()
        } else {
            if (mBtService != null) {
                if (mBtService?.state == BluetoothService.STATE_NONE) {
                    ///// STARTING BLUETOOTH SERVICE
                    mBtService?.start()
                }
            }
        }


        shimmer = Shimmer()
        ///// Shimmer is used to animate Text at Bottom
        shimmer.start(shimmerSwipe)


        /// Setting View at Bottom Swipable
        startSwiper()

        rvBluetooth.setOnClickListener {

            ///// If Bluetooth is not Enabled, User is dircected to settings to ENABLE IT
            if (!mBluetoothAdapter!!.isEnabled) {
                val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableIntent, REQUEST_ENABLE_BT)

            }

            if (mBtService == null) setupChat()

//            if (item.getTitle() == "ConnectBT") {
            // Launch the DeviceListActivity to see devices and do scan
            serverIntent = Intent(this, DeviceListActivity::class.java)
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE)
//            } else {
//                if (mBtService != null) {
//                    mBtService!!.stop()
//                    item.setTitle(R.string.connectbt)
//                }
//            }

        }

        mRequestingLocationUpdates = false
        mLastUpdateTime = ""

        // Update values using data stored in the Bundle.

        // Update values using data stored in the Bundle.
        updateValuesFromBundle(savedInstanceState)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mSettingsClient = LocationServices.getSettingsClient(this)

        // Kick off the process of building the LocationCallback, LocationRequest, and
        // LocationSettingsRequest objects.

        // Kick off the process of building the LocationCallback, LocationRequest, and
        // LocationSettingsRequest objects.
        createLocationCallback()
        createLocationRequest()
        buildLocationSettingsRequest()


    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CONNECT_DEVICE ->                 // When DeviceListActivity returns with a device to connect
                if (resultCode == RESULT_OK && data != null) {
                    connectDevice(data)
                }
            REQUEST_ENABLE_BT -> {
                if (mBtService == null) setupChat()
                if (resultCode == RESULT_OK) {
                    serverIntent = Intent(this, DeviceListActivity::class.java)
                    startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE)
                } else {
                    Toast.makeText(applicationContext, "BT device not enabled", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            REQUEST_CHECK_SETTINGS -> when (resultCode) {
                RESULT_OK -> {

                }
                RESULT_CANCELED -> {

                    mRequestingLocationUpdates = false
                    updateUI()
                }
            }
        }
    }


    ///// CREATING HANDLER FOR UPCOMING EVENTS FROM BLUETOOTH

    private val mBtHandler: Handler = @SuppressLint("HandlerLeak")
    object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_STATE_CHANGE -> when (msg.arg1) {
                    BluetoothService.STATE_CONNECTED -> {
                        textViewStatus.text = "Подключен к : $mConnectedDeviceName"
//                        Info.setText(R.string.title_connected)
                        tryconnect = false
                        resetvalues()
                        sendEcuMessage(RESET)
                    }
                    BluetoothService.STATE_CONNECTING -> {
                        textViewStatus.text = "Подключение ..."
//                        Info.setText(R.string.tryconnectbt)
                    }
                    BluetoothService.STATE_LISTEN, BluetoothService.STATE_NONE -> {
                        textViewStatus.text = "Не подключен"
                        resetvalues()
                    }
                }
                MESSAGE_WRITE -> {
                    try {
                        val writeBuf = msg.obj as ByteArray
                        val writeMessage = String(writeBuf)
                        if (commandmode || !initialized) {
//                            mConversationArrayAdapter?.add("Command:  $writeMessage")
                        }
                    } catch (e: java.lang.Exception) {

                    }

                }
                MESSAGE_READ -> {
                    val tmpmsg: String = clearMsg(msg)
//                    Info.setText(tmpmsg)

                    /*if (tmpmsg.contains(RSP_ID.NODATA.response) || tmpmsg.contains(RSP_ID.ERROR.response)) {

                        try{
                            String command = tmpmsg.substring(0,4);

                            if(isHexadecimal(command))
                            {
                                removePID(command);
                            }

                        }catch(Exception e)
                        {
                            Toast.makeText(getApplicationContext(), e.getMessage(),
                                Toast.LENGTH_LONG).show();
                        }
                    }*/if (commandmode || !initialized) {
//                        mConversationArrayAdapter?.add("$mConnectedDeviceName:  $tmpmsg")
                    }
                    Log.d("DASDASDSADA", "${msg}")
                    analysMsg(msg)
                }
                MESSAGE_DEVICE_NAME ->                     // save the connected device's name
                    mConnectedDeviceName = msg.data.getString(DEVICE_NAME)!!
                MESSAGE_TOAST -> Toast.makeText(
                    applicationContext, msg.data.getString(TOAST),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    ///// SENDING MESSAGE

    private fun sendEcuMessage(message: String) {
        var message = message

        if (mBtService != null) {
            // Check that we're actually connected before trying anything
            if (mBtService!!.state != BluetoothService.STATE_CONNECTED) {
                //Toast.makeText(this, R.string.not_connected, Toast.LENGTH_LONG).show();
                return
            }
            try {
                if (message.length > 0) {
                    message = message + "\r"
                    // Get the message bytes and tell the BluetoothChatService to write
                    val send = message.toByteArray()
                    mBtService!!.write(send)
                }
            } catch (e: java.lang.Exception) {
            }
        }
    }

    ///// PARSING DATA

    private fun analysMsg(msg: Message) {
        val tmpmsg: String = clearMsg(msg)
        generateVolt(tmpmsg)
        getElmInfo(tmpmsg)
        if (!initialized) {
            sendInitCommands()
        } else {
            checkPids(tmpmsg)
            if (!m_getPids && m_dedectPids == 1) {
                val sPIDs = "0100"
                sendEcuMessage(sPIDs)
                return
            }
            if (commandmode) {
                getFaultInfo(tmpmsg)
                return
            }
            try {
                analysPIDS(tmpmsg)
            } catch (e: java.lang.Exception) {
//                textViewStatus.text = "Error : " + e.message
            }
            sendDefaultCommands()
        }
    }

    private fun analysPIDS(dataRecieved: String) {
        Log.d("DASDASDSADA", "$dataRecieved")
        var dataRecieved: String? = dataRecieved
        var A = 0
        var B = 0
        var PID = 0
        if (dataRecieved != null
            && dataRecieved.matches("^[0-9A-F]+$".toRegex())
        ) {
            dataRecieved = dataRecieved.trim { it <= ' ' }
            val index = dataRecieved.indexOf("41")
            var tmpmsg: String? = null
            if (index != -1) {
                tmpmsg = dataRecieved.substring(index, dataRecieved.length)
                if (tmpmsg.substring(0, 2) == "41") {
                    PID = tmpmsg.substring(2, 4).toInt(16)
                    A = tmpmsg.substring(4, 6).toInt(16)
                    B = tmpmsg.substring(6, 8).toInt(16)
                    calculateEcuValues(PID, A, B)
                }
            }
        }
    }

    var INTAKE_TEMP = "0 °C"
    var THROTL = "0 %"
    var RAIL_PRESSURE = "0 кПа"
    var DISTANCE_TRAVELED = "0 км"
    var AMBIENT_TEMP = "0 °C"
    var TEMP_ENGINE_OIL = "0 °C"

    private fun calculateEcuValues(PID: Int, A: Int, B: Int) {
        Log.d("DASDASDSADA", "$PID , $A , $B")

        var `val` = 0.0
        var intval = 0
        var tempC = 0
        when (PID) {
            4 -> {

                // A*100/255
                `val` = (A * 100 / 255).toDouble()
                val calcLoad = `val`.toInt()
                textPressure.setText(Integer.toString(calcLoad) + " %")
//                mConversationArrayAdapter!!.add("Engine Load: " + Integer.toString(calcLoad) + " %")
                var FuelFlowLH = mMaf * calcLoad * mEnginedisplacement / 1000.0 / 714.0 + 0.8
                if (calcLoad == 0) FuelFlowLH = 0.0
                avgconsumption.add(FuelFlowLH)
                tempOil.setText(
                    String.format("%10.1f", calculateAverage(avgconsumption))
                        .trim { it <= ' ' } + " Л/ч")

            }
            5 -> {

                // A-40
                tempC = A - 40
                coolantTemp = tempC
                textViewTemp.setText(Integer.toString(coolantTemp) + " °C")

                //                mConversationArrayAdapter!!.add("Enginetemp: " + Integer.toString(tempC) + " °C")
            }
            11 -> {
                // A
                textCompress.text = "${Integer.toString(A)} кПа"
//                mConversationArrayAdapter!!.add("Intake Man Pressure: " + Integer.toString(A) + " kPa")
            }
            12 -> {

                //((A*256)+B)/4
                `val` = ((A * 256 + B) / 4).toDouble()
                intval = `val`.toInt()
                rpmval = intval
                textViewRound.setText("${intval} об/м")
            }
            13 -> {
                textViewSpeed.text = "$A kм/ч"
            }
            // A

            15 -> {

                // A - 40
                tempC = A - 40
                intakeairtemp = tempC
                INTAKE_TEMP = "$intakeairtemp °C"

//                tempOil.setText(Integer.toString(intakeairtemp) + " °C")
//                mConversationArrayAdapter!!.add("Intakeairtemp: " + Integer.toString(intakeairtemp) + " °C")
            }
            16 -> {

                // ((256*A)+B) / 100  [g/s]
                `val` = ((256 * A + B) / 100).toDouble()
                mMaf = `val`.toInt()
                textUsage.setText(Integer.toString(intval) + " г/с")
//                mConversationArrayAdapter!!.add("Maf Air Flow: " + Integer.toString(mMaf) + " g/s")
            }
            17 -> {

                //A*100/255
                `val` = (A * 100 / 255).toDouble()
                intval = `val`.toInt()
                THROTL = "$intval %"
//                mConversationArrayAdapter!!.add(" Throttle position: " + Integer.toString(intval) + " %")
            }
            35 -> {

                // ((A*256)+B)*0.079
                `val` = (A * 256 + B) * 0.079
                intval = `val`.toInt()
                RAIL_PRESSURE = "$intval кПа"
//                mConversationArrayAdapter!!.add("Fuel Rail Pressure: " + Integer.toString(intval) + " kPa")
            }
            49 -> {

                //(256*A)+B km
                `val` = (A * 256 + B).toDouble()
                intval = `val`.toInt()
                DISTANCE_TRAVELED = "$intval км"
//                mConversationArrayAdapter!!.add("Distance traveled: " + Integer.toString(intval) + " km")
            }
            70 -> {

                // A-40 [DegC]
                tempC = A - 40
                ambientairtemp = tempC
                AMBIENT_TEMP = "$ambientairtemp °C"
//                mConversationArrayAdapter!!.add("Ambientairtemp: " + Integer.toString(ambientairtemp) + " C°")
            }
            92 -> {

                //A-40
                tempC = A - 40
                engineoiltemp = tempC
                TEMP_ENGINE_OIL = "$engineoiltemp °C"
//                mConversationArrayAdapter!!.add("Engineoiltemp: " + Integer.toString(engineoiltemp) + " C°")
            }
            else -> {
            }
        }
    }

    private fun calculateAverage(listavg: List<Double>): Double {
        var sum = 0.0
        for (`val` in listavg) {
            sum += `val`
        }
        return sum / listavg.size
    }

    private fun getFaultInfo(tmpmsg: String) {
        var tmpmsg = tmpmsg
        var substr = "43"
        var index = tmpmsg.indexOf(substr)
        if (index == -1) {
            substr = "47"
            index = tmpmsg.indexOf(substr)
        }
        if (index != -1) {
            tmpmsg = tmpmsg.substring(index, tmpmsg.length)
            if (tmpmsg.substring(0, 2) == substr) {
                performCalculations(tmpmsg)
                var faultCode: String? = null
                var faultDesc: String? = null
                if (troubleCodesArray.size > 0) {
                    for (i in troubleCodesArray.indices) {
                        faultCode = troubleCodesArray[i]
                        faultDesc = troubleCodes!!.getFaultCode(faultCode)
                        Log.e(
                            TAG,
                            "Fault Code: $substr : $faultCode desc: $faultDesc"
                        )
                        if (faultCode != null && faultDesc != null) {
//                            mConversationArrayAdapter!!.add("$mConnectedDeviceName:  TroubleCode -> $faultCode\n$faultDesc")
                        } else if (faultCode != null && faultDesc == null) {
//                            mConversationArrayAdapter!!.add(
//                                """
//                                $mConnectedDeviceName:  TroubleCode -> $faultCode
//                                Definition not found for code: $faultCode
//                                """.trimIndent()
//                            )
                        }
                    }
                } else {
                    faultCode = "No error found..."
//                    mConversationArrayAdapter!!.add("$mConnectedDeviceName:  TroubleCode -> $faultCode")
                }
            }
        }
    }

    protected fun performCalculations(fault: String) {
        var workingData = ""
        val startIndex = 0
        troubleCodesArray.clear()
        try {
            if (fault.indexOf("43") != -1) {
                workingData = fault.replace("^43|[\r\n]43|[\r\n]".toRegex(), "")
            } else if (fault.indexOf("47") != -1) {
                workingData = fault.replace("^47|[\r\n]47|[\r\n]".toRegex(), "")
            }
            var begin = startIndex
            while (begin < workingData.length) {
                var dtc = ""
                val b1: Byte = hexStringToByteArray(workingData[begin])
                val ch1: Int = (b1 and 0xC0.toByte()).toInt() shr 6
                val ch2: Int = (b1 and 0x30).toInt() shr 4
                dtc += dtcLetters.get(ch1)
                dtc += hexArray.get(ch2)
                dtc += workingData.substring(begin + 1, begin + 4)
                if (dtc == "P0000") {
                    begin += 4
                    continue
                }
                troubleCodesArray.add(dtc)
                begin += 4
            }
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Error: " + e.message)
        }
    }

    private fun hexStringToByteArray(s: Char): Byte {
        return (Character.digit(s, 16) shl 4).toByte()
    }


    private fun getElmInfo(tmpmsg: String) {
        if (tmpmsg.contains("ELM") || tmpmsg.contains("elm")) {
            devicename = tmpmsg
        }
        if (tmpmsg.contains("SAE") || tmpmsg.contains("ISO")
            || tmpmsg.contains("sae") || tmpmsg.contains("iso") || tmpmsg.contains("AUTO")
        ) {
            deviceprotocol = tmpmsg
        }
        if (deviceprotocol != null && devicename != null) {
            devicename = devicename!!.replace("STOPPED".toRegex(), "")
            deviceprotocol = deviceprotocol!!.replace("STOPPED".toRegex(), "")
//            textViewStatus.setText("$devicename $deviceprotocol")
        }
    }

    private fun checkPids(tmpmsg: String) {
        if (tmpmsg.indexOf("41") != -1) {
            val index = tmpmsg.indexOf("41")
            val pidmsg = tmpmsg.substring(index, tmpmsg.length)
            if (pidmsg.contains("4100")) {
                setPidsSupported(pidmsg)
                return
            }
        }
    }

    private fun setPidsSupported(buffer: String) {
//        textViewStatus.setText("Trying to get available pids : $trycount")
        trycount++
        val flags = java.lang.StringBuilder()
        var buf = buffer
        buf = buf.trim { it <= ' ' }
        buf = buf.replace("\t", "")
        buf = buf.replace(" ", "")
        buf = buf.replace(">", "")
        if (buf.indexOf("4100") == 0 || buf.indexOf("4120") == 0) {
            for (i in 0..7) {
                val tmp = buf.substring(i + 4, i + 5)
                val data = Integer.valueOf(tmp, 16).toInt()
                //                String retStr = Integer.toBinaryString(data);
                if (data and 0x08 == 0x08) {
                    flags.append("1")
                } else {
                    flags.append("0")
                }
                if (data and 0x04 == 0x04) {
                    flags.append("1")
                } else {
                    flags.append("0")
                }
                if (data and 0x02 == 0x02) {
                    flags.append("1")
                } else {
                    flags.append("0")
                }
                if (data and 0x01 == 0x01) {
                    flags.append("1")
                } else {
                    flags.append("0")
                }
            }
            commandslist.clear()
            commandslist.add(0, VOLTAGE)
            var pid = 1
            val supportedPID = java.lang.StringBuilder()
            supportedPID.append("Supported PIDS:\n")
            for (j in 0 until flags.length) {
                if (flags[j] == '1') {
                    supportedPID.append(" " + PIDS.get(j) + " ")
                    if (!PIDS.get(j).contains("11") && !PIDS.get(j)
                            .contains("01") && !PIDS.get(j).contains("20")
                    ) {
                        commandslist.add(pid, "01" + PIDS.get(j))
                        pid++
                    }
                }
            }
            m_getPids = true
//            mConversationArrayAdapter!!.add("$mConnectedDeviceName: $supportedPID")
            whichCommand = 0
            sendEcuMessage("ATRV")
        } else {
            return
        }
    }


    private fun clearMsg(msg: Message): String {
        var tmpmsg = msg.obj.toString()
        tmpmsg = tmpmsg.replace("null", "")
        tmpmsg = tmpmsg.replace("\\s".toRegex(), "") //removes all [ \t\n\x0B\f\r]
        tmpmsg = tmpmsg.replace(">".toRegex(), "")
        tmpmsg = tmpmsg.replace("SEARCHING...".toRegex(), "")
        tmpmsg = tmpmsg.replace("ATZ".toRegex(), "")
        tmpmsg = tmpmsg.replace("ATI".toRegex(), "")
        tmpmsg = tmpmsg.replace("atz".toRegex(), "")
        tmpmsg = tmpmsg.replace("ati".toRegex(), "")
        tmpmsg = tmpmsg.replace("ATDP".toRegex(), "")
        tmpmsg = tmpmsg.replace("atdp".toRegex(), "")
        tmpmsg = tmpmsg.replace("ATRV".toRegex(), "")
        tmpmsg = tmpmsg.replace("atrv".toRegex(), "")
        return tmpmsg
    }

    private fun generateVolt(msg: String?) {
        Log.d("DASDASDSADA", "${msg}")
        var VoltText: String? = null
        if (msg != null
            && msg.matches("\\s*[0-9]{1,2}([.][0-9]{1,2})\\s*".toRegex())
        ) {
            VoltText = msg + " B"
//            mConversationArrayAdapter?.add(mConnectedDeviceName + ": " + msg + "V")
        } else if (msg != null
            && msg.matches("\\s*[0-9]{1,2}([.][0-9]{1,2})?V\\s*".toRegex())
        ) {
            VoltText = msg
//            mConversationArrayAdapter?.add("$mConnectedDeviceName: $msg")
        }
        if (VoltText != null) {
            textVolt.text = VoltText
        }
    }


    private fun sendInitCommands() {
        if (initializeCommands.size != 0) {
            if (whichCommand < 0) {
                whichCommand = 0
            }
            val send = initializeCommands[whichCommand]
            sendEcuMessage(send)
            if (whichCommand == initializeCommands.size - 1) {
                initialized = true
                whichCommand = 0
                sendDefaultCommands()
            } else {
                whichCommand++
            }
        }
    }

    private fun sendDefaultCommands() {
        if (commandslist.size != 0) {
            if (whichCommand < 0) {
                whichCommand = 0
            }
            val send = commandslist[whichCommand]
            sendEcuMessage(send)
            if (whichCommand >= commandslist.size - 1) {
                whichCommand = 0
            } else {
                whichCommand++
            }
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        getPreferences()
        if (mRequestingLocationUpdates!! && checkPermissions()) {
            startLocationUpdates()
        } else if (!checkPermissions()) {
            requestPermissions()
        }
        updateUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mBtService != null) mBtService!!.stop()
        if(::countDownTimer.isInitialized){
            countDownTimer.cancel()
        }
    }

    override fun onStart() {
        super.onStart()
        getPreferences()
        resetvalues()
    }


    private fun setupChat() {

        // Initialize the BluetoothChatService to perform bluetooth connections
        mBtService = BluetoothService(this, mBtHandler)
    }

    var EXTRA_DEVICE_ADDRESS = "device_address"

    private fun connectDevice(data: Intent) {
        tryconnect = true
        // Get the device MAC address
        val address = data.extras!!.getString(EXTRA_DEVICE_ADDRESS)
        // Get the BluetoothDevice object
        val device = mBluetoothAdapter!!.getRemoteDevice(address)
        try {
            // Attempt to connect to the device
            mBtService!!.connect(device)
            currentdevice = device
        } catch (e: Exception) {
        }
    }


    private fun startSwiper() {

        val displayMetrics: DisplayMetrics = resources.displayMetrics
        val dpWidth = displayMetrics.widthPixels

        rvSwiper.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event!!.action) {
                    MotionEvent.ACTION_DOWN -> {


                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (event.rawX < 768) {
                            rvSwiper.translationX = event.rawX - 20
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (event.rawX < 600) {
                            resetvalues()
                        } else {

                            vibratePhone(70)

                            rvSwiper.animate()
                                .translationX(dpWidth.toFloat() - 2.1f * rvSwiper.width)
                                .setDuration(400)
                                .setInterpolator(DecelerateInterpolator()).start()
                            progressBar.visibility = View.VISIBLE
                            arrowRight.visibility = View.GONE
                            shimmerSwipe.text = "Отправка ..."

//                            rvSwiper.postDelayed({
                            startSending()
//                            },1000)
                        }
                    }
                    else -> return false
                }
                return true
            }

        })

    }

    var DATA_SENT = 0
    lateinit var countDownTimer: CountDownTimer

    var FIREBASE_INTERVAL = 60000L


    fun startSending() {
        startLocationUpdates()

        countDownTimer = object : CountDownTimer(Long.MAX_VALUE, FIREBASE_INTERVAL) {
            override fun onTick(millisUntilFinished: Long) {

                val myRef =
                    Firebase.database("https://carcheck-49e91-default-rtdb.europe-west1.firebasedatabase.app/")
                        .getReference()

                val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val currentDateandTime: String = sdf.format(Date())

                val carData = CarData(
                    deviceprotocol ?: "0",
                    textViewSpeed.text.toString(),
                    textPressure.text.toString(),
                    textViewRound.text.toString(),
                    textViewTemp.text.toString(),
                    tempOil.text.toString(),
                    textUsage.text.toString(),
                    textCompress.text.toString(),
                    textVolt.text.toString(),
                    INTAKE_TEMP,
                    THROTL,
                    RAIL_PRESSURE,
                    DISTANCE_TRAVELED,
                    AMBIENT_TEMP,
                    TEMP_ENGINE_OIL,
                    mCurrentLocation?.latitude.toString(),
                    mCurrentLocation?.longitude.toString(),
                )

                if (mCurrentLocation!=null){
                    myRef.child(mConnectedDeviceName ?: "NOT AVAILABLE").child(currentDateandTime)
                        .setValue(carData)
                        .addOnSuccessListener {
                            DATA_SENT++
                            textSentData.text = "$DATA_SENT"
                        }
                        .addOnFailureListener {
                            Toast.makeText(
                                this@MainActivity,
                                "Failed to write to Firebase",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }

            }

            override fun onFinish() {

            }
        }
        countDownTimer.start()

    }

    fun vibratePhone(duration: Long) {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(
                VibrationEffect.createOneShot(
                    duration,
                    VibrationEffect.EFFECT_TICK
                )
            )
        } else {
            v.vibrate(duration)
        }
    }

    fun isHexadecimal(text: String): Boolean {
        var text = text
        text = text.trim { it <= ' ' }
        val hexDigits = charArrayOf(
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'a', 'b', 'c', 'd', 'e', 'f', 'A', 'B', 'C', 'D', 'E', 'F'
        )
        var hexDigitsCount = 0
        for (symbol in text.toCharArray()) {
            for (hexDigit in hexDigits) {
                if (symbol == hexDigit) {
                    hexDigitsCount++
                    break
                }
            }
        }
        return if (true) hexDigitsCount == text.length else false
    }


    private fun getPreferences() {

        if (m_dedectPids == 0) {
            commandslist.clear()
            var i = 0
            commandslist.add(i, VOLTAGE)
            commandslist.add(i, ENGINE_RPM)
            i++
            commandslist.add(i, VEHICLE_SPEED)
            i++
            commandslist.add(i, ENGINE_LOAD)
            i++
            commandslist.add(i, ENGINE_COOLANT_TEMP)
            i++
            commandslist.add(i, TEMP_ENGINE_OIL)
            i++
            commandslist.add(i, INTAKE_TEMP)
            i++
            commandslist.add(i, MAF_AIR_FLOW)
            whichCommand = 0
        }
    }

    fun resetvalues() {

        if (::countDownTimer.isInitialized) {
            countDownTimer.cancel()
        }

        stopLocationUpdates()

        rvSwiper.animate().translationX(0f).setDuration(400)
            .setInterpolator(DecelerateInterpolator()).start()
        progressBar.visibility = View.GONE
        arrowRight.visibility = View.VISIBLE
        shimmerSwipe.text = "Проведите, чтобы отправить"


        textViewSpeed.text = "0 kм/ч"
        textPressure.text = "0%"
        textViewRound.text = "0 об/м"
        textViewTemp.text = "0 °C"
        tempOil.text = "0 л/ч"
        textUsage.text = "0 г/с"
        textCompress.text = "0 кПа"
        textVolt.text = "0 В"

        m_getPids = false
        whichCommand = 0
        trycount = 0
        initialized = false
        defaultStart = false
        avgconsumption.clear()
//        mConversationArrayAdapter?.clear()
    }

    ////////// ALL THE LOCATION LOGIC GOES HERE

    private val REQUEST_PERMISSIONS_REQUEST_CODE = 34

    /**
     * Constant used in the location settings dialog.
     */
    private val REQUEST_CHECK_SETTINGS = 0x1

    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    private val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 10000

    /**
     * The fastest rate for active location updates. Exact. Updates will never be more frequent
     * than this value.
     */
    private val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2

    // Keys for storing activity state in the Bundle.
    private val KEY_REQUESTING_LOCATION_UPDATES = "requesting-location-updates"
    private val KEY_LOCATION = "location"
    private val KEY_LAST_UPDATED_TIME_STRING = "last-updated-time-string"

    /**
     * Provides access to the Fused Location Provider API.
     */
    private var mFusedLocationClient: FusedLocationProviderClient? = null

    /**
     * Provides access to the Location Settings API.
     */
    private var mSettingsClient: SettingsClient? = null

    /**
     * Stores parameters for requests to the FusedLocationProviderApi.
     */
    private var mLocationRequest: LocationRequest? = null

    /**
     * Stores the types of location services the client is interested in using. Used for checking
     * settings to determine if the device has optimal location settings.
     */
    private var mLocationSettingsRequest: LocationSettingsRequest? = null

    /**
     * Callback for Location events.
     */
    private var mLocationCallback: LocationCallback? = null

    /**
     * Represents a geographical location.
     */
    private var mCurrentLocation: Location? = null

    private var mRequestingLocationUpdates: Boolean? = null

    /**
     * Time when the location was updated represented as a String.
     */
    private var mLastUpdateTime: String? = null

    private fun updateValuesFromBundle(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            // Update the value of mRequestingLocationUpdates from the Bundle, and make sure that
            // the Start Updates and Stop Updates buttons are correctly enabled or disabled.
            if (savedInstanceState.keySet()
                    .contains(KEY_REQUESTING_LOCATION_UPDATES)
            ) {
                mRequestingLocationUpdates = savedInstanceState.getBoolean(
                    KEY_REQUESTING_LOCATION_UPDATES
                )
            }

            // Update the value of mCurrentLocation from the Bundle and update the UI to show the
            // correct latitude and longitude.
            if (savedInstanceState.keySet().contains(KEY_LOCATION)) {
                // Since KEY_LOCATION was found in the Bundle, we can be sure that mCurrentLocation
                // is not null.
                mCurrentLocation = savedInstanceState.getParcelable(KEY_LOCATION)
            }

            // Update the value of mLastUpdateTime from the Bundle and update the UI.
            if (savedInstanceState.keySet().contains(KEY_LAST_UPDATED_TIME_STRING)) {
                mLastUpdateTime =
                    savedInstanceState.getString(KEY_LAST_UPDATED_TIME_STRING)
            }
            updateUI()
        }
    }


    private fun createLocationRequest() {
        mLocationRequest = LocationRequest()

        // Sets the desired interval for active location updates. This interval is
        // inexact. You may not receive updates at all if no location sources are available, or
        // you may receive them slower than requested. You may also receive updates faster than
        // requested if other applications are requesting location at a faster interval.
        mLocationRequest?.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS)

        // Sets the fastest rate for active location updates. This interval is exact, and your
        // application will never receive updates faster than this value.
        mLocationRequest?.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS)
        mLocationRequest?.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
    }

    /**
     * Creates a callback for receiving location events.
     */
    private fun createLocationCallback() {
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                mCurrentLocation = locationResult.lastLocation
                mLastUpdateTime = DateFormat.getTimeInstance().format(Date())
                updateLocationUI()
            }
        }
    }

    /**
     * Uses a [com.google.android.gms.location.LocationSettingsRequest.Builder] to build
     * a [com.google.android.gms.location.LocationSettingsRequest] that is used for checking
     * if a device has the needed location settings.
     */
    private fun buildLocationSettingsRequest() {
        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(mLocationRequest!!)
        mLocationSettingsRequest = builder.build()
    }

    /**
     * Handles the Start Updates button and requests start of location updates. Does nothing if
     * updates have already been requested.
     */
    fun startLocationUpdates(view: View?) {
        if (!mRequestingLocationUpdates!!) {
            mRequestingLocationUpdates = true
            startLocationUpdates()
        }
    }

    /**
     * Handles the Stop Updates button, and requests removal of location updates.
     */
    fun stopLocationUpdates(view: View?) {
        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        stopLocationUpdates()
    }

    /**
     * Requests location updates from the FusedLocationApi. Note: we don't call this unless location
     * runtime permission has been granted.
     */
    private fun startLocationUpdates() {
        // Begin by checking if the device has the necessary location settings.
        mSettingsClient!!.checkLocationSettings(mLocationSettingsRequest)
            .addOnSuccessListener(this) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    mFusedLocationClient!!.requestLocationUpdates(
                        mLocationRequest,
                        mLocationCallback, Looper.myLooper()
                    )
                }

                updateUI()
            }
            .addOnFailureListener(this) { e ->
                val statusCode = (e as ApiException).statusCode
                when (statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {

                        try {
                            // Show the dialog by calling startResolutionForResult(), and check the
                            // result in onActivityResult().
                            val rae = e as ResolvableApiException
                            rae.startResolutionForResult(
                                this@MainActivity,
                                REQUEST_CHECK_SETTINGS
                            )
                        } catch (sie: SendIntentException) {
                        }
                    }
                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                        val errorMessage = "Location settings are inadequate, and cannot be " +
                                "fixed here. Fix in Settings."
                        Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                        mRequestingLocationUpdates = false
                    }
                }
                updateUI()
            }
    }

    /**
     * Updates all UI fields.
     */
    private fun updateUI() {
        updateLocationUI()
    }


    /**
     * Sets the value of the UI fields for the location latitude, longitude and last update time.
     */
    private fun updateLocationUI() {
        if (mCurrentLocation != null) {
            textLatitude.text = "${mCurrentLocation?.latitude}"
            textLongitude.text = "${mCurrentLocation?.longitude}"
        }
    }

    /**
     * Removes location updates from the FusedLocationApi.
     */
    private fun stopLocationUpdates() {
        if (!mRequestingLocationUpdates!!) {
            return
        }

        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        mFusedLocationClient!!.removeLocationUpdates(mLocationCallback)
            .addOnCompleteListener(this) {
                mRequestingLocationUpdates = false
            }
    }



    /**
     * Stores activity data in the Bundle.
     */
    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        savedInstanceState.putBoolean(
            KEY_REQUESTING_LOCATION_UPDATES,
            mRequestingLocationUpdates!!
        )
        savedInstanceState.putParcelable(KEY_LOCATION, mCurrentLocation)
        savedInstanceState.putString(KEY_LAST_UPDATED_TIME_STRING, mLastUpdateTime)
        super.onSaveInstanceState(savedInstanceState)
    }

    /**
     * Shows a [Snackbar].
     *
     * @param mainTextStringId The id for the string resource for the Snackbar text.
     * @param actionStringId   The text of the action item.
     * @param listener         The listener associated with the Snackbar action.
     */
    private fun showSnackbar(
        mainTextStringId: Int, actionStringId: Int,
        listener: View.OnClickListener
    ) {
        Snackbar.make(
            findViewById(android.R.id.content),
            getString(mainTextStringId),
            Snackbar.LENGTH_INDEFINITE
        )
            .setAction(getString(actionStringId), listener).show()
    }

    /**
     * Return the current state of the permissions needed.
     */
    private fun checkPermissions(): Boolean {
        val permissionState = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        return permissionState == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        val shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            showSnackbar(
                R.string.permission_rationale,
                android.R.string.ok
            ) { // Request permission
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_PERMISSIONS_REQUEST_CODE
                )
            }
        } else {
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            ActivityCompat.requestPermissions(
                this@MainActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {

        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.size <= 0) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.

            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mRequestingLocationUpdates!!) {
                    startLocationUpdates()
                }
            }
        }
    }


}