// -*- mode: java; c-basic-offset: 2; -*-
//
// Copyright 2014 - David Garrett - Broadcom Corporation
// http://www.apache.org/licenses/LICENSE-2.0
//
//

package com.google.appinventor.components.runtime;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;


import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.common.YaVersion;
import com.google.appinventor.components.runtime.util.SdkLevel;
import com.google.appinventor.components.runtime.util.ErrorMessages;


import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

/**
 * The WICEDSense component connects to the BLTE device
 *
 * @author  David Garrett (not the violionist)
 */
@DesignerComponent(version = YaVersion.WICEDSENSE_COMPONENT_VERSION,
    category = ComponentCategory.CONNECTIVITY,
    description = "The WICEDSense component is still experimental",
    nonVisible = true,
    iconName = "images/bluetooth.png")
@SimpleObject
@UsesPermissions(permissionNames = 
                 "android.permission.BLUETOOTH, " + 
                 "android.permission.BLUETOOTH_ADMIN")
public final class WICEDSense extends AndroidNonvisibleComponent implements Component {

  private static final String LOG_TAG = "WICEDSense";
  private final Activity activity;

  // if constructor finds enabled BTLE device, this is set
  private boolean isEnabled = false;
  
  // Start with no scan
  private boolean scanning = false;

  // Holds the link to the Bluetooth Adapter
  private BluetoothAdapter bluetoothAdapter;

  // holds error message
  private boolean mLogEnabled = true;
  private String mLogMessage = "";

  // holds the BT device
  private int deviceRssi = -130;
  private BluetoothDevice mDevice;
  
  // Holds list of devices
  private int numDevices = 0;
  private ArrayList<BluetoothDevice> mLeDevices;
  // not sure why but leDeviceListAdapter was crashing -need to debug
  // private LeDeviceListAdapter leDeviceListAdapter;
  
  // holds sensors data
  private boolean mSensorsEnabled = false;
  
  // Gatt client pointer
  private BluetoothGatt mBluetoothGatt;

  // Service founds
  private List<BluetoothGattService> mGattServices;

  // holds current connection state
  private int mConnectionState = STATE_DISCONNECTED;

  // Holds specific WICED services
  private boolean validWICEDDevice = false;
  private BluetoothGattService mSensorService = null;
  private BluetoothGattCharacteristic mSensorNotification = null;
  private BluetoothGattService mBatteryService = null;
  private BluetoothGattCharacteristic mBatteryCharacteristic = null;

  // Holds Battery level
  private int batteryLevel = -1;

  private String mXAccel = "";
  private String mYAccel = "";
  private String mZAccel = "";
  
  // Defines BTLE States
  private static final int STATE_DISCONNECTED = 0;
  private static final int STATE_CONNECTING = 1;
  private static final int STATE_CONNECTED = 2;


  /** Descriptor used to enable/disable notifications/indications */
  private static final UUID CLIENT_CONFIG_UUID = UUID
          .fromString("00002902-0000-1000-8000-00805f9b34fb");
  private static final UUID SENSOR_SERVICE_UUID = UUID
          .fromString("739298B6-87B6-4984-A5DC-BDC18B068985");
  private static final UUID SENSOR_NOTIFICATION_UUID = UUID
          .fromString("33EF9113-3B55-413E-B553-FEA1EAADA459");
  private static final UUID BATTERY_SERVICE_UUID = UUID
          .fromString("0000180F-0000-1000-8000-00805f9b34fb");
  private static final UUID BATTERY_LEVEL_UUID = UUID
          .fromString("00002a19-0000-1000-8000-00805f9b34fb");


  /**
   * Creates a new WICEDSense component.
   *
   * @param container the enclosing component
   */
  public WICEDSense (ComponentContainer container) {
    super(container.$form());
    activity = container.$context();

    // names the function
    String functionName = "WICEDSense";

    // setup new list of devices
    mLeDevices = new ArrayList<BluetoothDevice>();

    // initialize GATT services
    mGattServices = new ArrayList<BluetoothGattService>();

    /* Setup the Bluetooth adapter */
    if (SdkLevel.getLevel() < SdkLevel.LEVEL_JELLYBEAN_MR2) { 
      bluetoothAdapter = null;
      /** issues message to reader */
      form.dispatchErrorOccurredEvent(this, functionName,
          ErrorMessages.ERROR_BLUETOOTH_LE_NOT_SUPPORTED);
    } else { 
      bluetoothAdapter = newBluetoothAdapter(activity);
    }

    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) { 
      isEnabled = false;
      LogMessage("No Valid BTLE Device on platform", "e");

      /** issues message to reader */
      form.dispatchErrorOccurredEvent(this, "WICEDSense",
          ErrorMessages.ERROR_BLUETOOTH_NOT_ENABLED);

    } else { 
//      Log.i(LOG_TAG, "Found valid BTLE Device");
      isEnabled = true;
      LogMessage("Found the BTLE Device on platform", "i");
    }

  }

  /** Log Messages */
  private void LogMessage(String message, String level) { 
    if (mLogEnabled) { 
      mLogMessage = message;
      String errorLevel = "e";
      String warningLevel = "w";
  
      // push to appropriate logging
      if (level.equals(errorLevel)) {
        Log.e(LOG_TAG, message);
      } else if (level.equals(warningLevel)) {
        Log.w(LOG_TAG, message);
      } else { 
        Log.i(LOG_TAG, message);
      }
  
      // trigger event
      Info();
    }
  }

  /** ----------------------------------------------------------------------
   *  BTLE Code Section
   *  ----------------------------------------------------------------------
   */

  /** create adaptor */
  public static BluetoothAdapter newBluetoothAdapter(Context context) {
    final BluetoothManager bluetoothManager = 
      (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    return bluetoothManager.getAdapter();  
  }

  /** Device scan callback. */
  //private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
  private LeScanCallback mLeScanCallback = new LeScanCallback() {
    @Override
    public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {

      // Record the first device you find
      if (!mLeDevices.contains(device)) { 
        mLeDevices.add(device);
        numDevices++;

        // Trigger device event
        LogMessage("Found a new BTLE device in scan", "i");
        FoundDevice();
      }
    }
  };

  /** Get Sensor Message in HEX */
  final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
  private static String bytesToHex(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for ( int j = 0; j < bytes.length; j++ ) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = hexArray[v >>> 4];
      hexChars[j * 2 + 1] = hexArray[v & 0x0F];
    }
    return new String(hexChars);
  }

  /** Various callback methods defined by the BLE API. */
  private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
          //String intentAction;
          if (newState == BluetoothProfile.STATE_CONNECTED) {
            mConnectionState = STATE_CONNECTED;
            LogMessage("Connected to BLTE device", "i");

            // Trigger device discovery 
            mBluetoothGatt.discoverServices();

          } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
             mConnectionState = STATE_DISCONNECTED;

             // null out services
             mSensorService = null;
             mSensorNotification = null;
             mBatteryService = null;
             mBatteryCharacteristic = null;
             mGattServices.clear();

             LogMessage("Disconnected from BLTE device", "i");
          }
        }

        @Override
        // New services discovered
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
          deviceRssi = rssi;
          LogMessage("Updating RSSI from remove device = " + rssi + " dBm", "i");

          // update RSSI
          RSSIUpdated();
        }
        
        @Override
        // New services discovered
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
          if (status == BluetoothGatt.GATT_SUCCESS) {
            // record services 
            mGattServices = gatt.getServices();
            validWICEDDevice = true;
  
            // log message
            LogMessage("Found " + mGattServices.size() + " Device services", "i");

            // Match to sensor services
            BluetoothGattService mService;
            for (int loop1 = 0; loop1 < mGattServices.size(); loop1++) {
              mService = mGattServices.get(loop1);
              // get battery service
              if (BATTERY_SERVICE_UUID.equals(mService.getUuid())) { 
                mBatteryService = mService;
                mBatteryCharacteristic = mBatteryService.getCharacteristic(BATTERY_LEVEL_UUID);
              } 
              // get the sensor service
              if (SENSOR_SERVICE_UUID.equals(mService.getUuid())) { 
                mSensorService = mService;
                mSensorNotification = mSensorService.getCharacteristic(SENSOR_NOTIFICATION_UUID);
              } 
            }

            // Check for VALID WICED service
            validWICEDDevice = true;
            if (mBatteryService == null) { validWICEDDevice = false; }
            if (mBatteryCharacteristic == null) { validWICEDDevice = false; }
            if (mSensorService == null) { validWICEDDevice = false; }
            if (mSensorNotification == null) { validWICEDDevice = false; }

            // Warnings if not valid
            if (validWICEDDevice) { 
              LogMessage("Found service on WICED Sense device", "i");
              // turn on notifications if needed
              SensorsEnabled(mSensorsEnabled);

            } else { 
              LogMessage("Connected device is not a WICED Sense kit", "e");
              /** issues message to reader */
             // form.dispatchErrorOccurredEvent(this, "onServicesDiscovered",
             //     ErrorMessages.ERROR_NON_WICED_SENSE_DEVICE);
            }

            // Triggers callback for connected device
            Connected();
          } else {
            LogMessage("onServicesDiscovered received but failed", "e");
          }
        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt, 
                                         BluetoothGattCharacteristic characteristic, 
                                         int status) {
          if (status == BluetoothGatt.GATT_SUCCESS) {
            if (BATTERY_LEVEL_UUID.equals(characteristic.getUuid())) {
              try {
                batteryLevel = characteristic.getIntValue(
                               BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                LogMessage("Read battery level " + batteryLevel + "%", "i");

                // trigger event
                BatteryLevelUpdated();
              } catch (Throwable t) {
                LogMessage("Unable to read battery level.", "e");
                return;
              }
            }
          } else {
            LogMessage("Failure in reading Gatt Characteristics", "e");
          }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                         BluetoothGattCharacteristic characteristic) {
          if (SENSOR_NOTIFICATION_UUID.equals(characteristic.getUuid())) {
            byte[] value = characteristic.getValue();
            mXAccel = bytesToHex(value);
            LogMessage("Reading back sensor data", "i");
            // Set notification     
            SensorsUpdated();
          }
        }
   };


  /** ----------------------------------------------------------------------
   *  ----------------------------------------------------------------------
   *  GUI Interface Code Section
   *  ----------------------------------------------------------------------
   *  ----------------------------------------------------------------------
   */

  /**  ----------------------------------------------------------------------
   *   Events 
   *   ----------------------------------------------------------------------
   */

  /**
   * Callback for LogMessages
   */
  @SimpleEvent(description = "Event the log message is updated.")
  public void Info() { 
    EventDispatcher.dispatchEvent(this, "Info");
  }

  /**
   * Callback for Found Device Event
   */
  @SimpleEvent(description = "Event when an LE Device is found in scan.")
  public void FoundDevice() { 
    EventDispatcher.dispatchEvent(this, "FoundDevice");
  }

  /**
   * Callback for RSSI data
   */
  @SimpleEvent(description = "RSSI Read Event.")
  public void RSSIUpdated() { 
    EventDispatcher.dispatchEvent(this, "RSSIUpdated");
  }
 
  /**
   * Callback events for device connection
   */
  @SimpleEvent(description = "BTLE Connection Event.")
  public void Connected() { 
    EventDispatcher.dispatchEvent(this, "Connected");
  }

  /**
   * Callback events for Sensor Update
   */
  @SimpleEvent(description = "Sensor data updated.")
  public void SensorsUpdated() { 
    EventDispatcher.dispatchEvent(this, "SensorsUpdated");
  }

  /**
   * Callback events for batery levels
   */
  @SimpleEvent(description = "Received Battery Level.")
  public void BatteryLevelUpdated() { 
    EventDispatcher.dispatchEvent(this, "BatteryLevelUpdated");
  }

  /**  ----------------------------------------------------------------------
   *   Function calls
   *   ----------------------------------------------------------------------
   */

  /**
   * Allows the user to check battery level
   */
  @SimpleFunction(description = "Starts BTLE scanning")
  public void ReadBatteryLevel() { 
    String functionName = "ReadBatteryLevel";
    if (mConnectionState == STATE_CONNECTED) { 
      if (validWICEDDevice) { 
        mBluetoothGatt.readCharacteristic(mBatteryCharacteristic);
        LogMessage("Reading battery characteristic", "i");
      }
    } else { 
      LogMessage("Trying to reading battery before connected", "e");
    }
  }

  /**
   * Allows the user to start the scan
   */
  @SimpleFunction(description = "Starts BTLE scanning")
  public void startLeScan() { 
    String functionName = "startLeScan";
    
    // If not scanning, clear list rescan
    if (!scanning) { 
      numDevices = 0;
      mLeDevices.clear();
      scanning = true;

      // Force the LE scan
      try { 
        bluetoothAdapter.startLeScan(mLeScanCallback);
      } catch (Exception e) { 
        LogMessage("Failed to start LE scan", "e");
        scanning = false;
      }
    }
  }

  /**
   * Allows the user to Stop the scan
   */
  @SimpleFunction(description = "Stops BTLE scanning")
  public void stopLeScan() { 
    String functionName = "stopLeScan";
    if (scanning) { 
      try { 
        bluetoothAdapter.stopLeScan(mLeScanCallback);
        scanning = false;
      } catch (Exception e) { 
        LogMessage("Failed to stop LE scan", "e");
      }
    }
  }

  /**  ----------------------------------------------------------------------
   *   Properties of the Device
   *   ----------------------------------------------------------------------
   */

  /** Gets Battery Level */
  @SimpleProperty(description = "Queries Connected state", 
                  category = PropertyCategory.BEHAVIOR,
                  userVisible = true)
  public int BatteryLevel() {
    return batteryLevel;
  }

  /** Makes sure GATT profile is connected */
  @SimpleProperty(description = "Queries Connected state", 
                  category = PropertyCategory.BEHAVIOR,
                  userVisible = true)
  public boolean IsConnected() {
    if (mConnectionState == STATE_CONNECTED) { 
      return true;
    } else { 
      return false;
    }
  }

  /** Returns the RSSI measurement from devices
   *
   *  Instantly returns the rssi on WICEDsene class variable
   *  but calls a Gatt callback that will update with the new 
   *  values on the callback (later in time)
   *
   *  Should consider callback "EVENT" to get accurate value
   */
  @SimpleProperty(description = "Queries RSSI", 
                  category = PropertyCategory.BEHAVIOR,
                  userVisible = true)
  public int RSSI() {
    return deviceRssi;
  }

  /**
   * Returns text log
   */
  @SimpleProperty(description = "Queries Text", 
                  category = PropertyCategory.BEHAVIOR,
                  userVisible = true)
  public String Text() {
    return mLogMessage;
  }

  /**
   * Allows the user to Read remote RSSI
   */
  @SimpleFunction(description = "Forces read of remote RSSI")
  public void ReadRSSI() { 
    String functionName = "ReadRSSI";
    if (mConnectionState == STATE_CONNECTED) { 
      mBluetoothGatt.readRemoteRssi();
    } else { 
      LogMessage("Trying to read RSSI without a connected device", "e");
    }
  }

  /**
   * Allows the user to disconnect
   */
  @SimpleFunction(description = "Disconnects GATT connection")
  public void Disconnect() { 
    String functionName = "Disconnect";

    if (mConnectionState == STATE_CONNECTED) { 
      mBluetoothGatt.disconnect();
    } else { 
      LogMessage("Trying to disconnect without a connected device", "e");
    }
  }

//  /**
//   * Sets up device discovery
//   */
//  @SimpleFunction(description = "Initiates a service discovery")
//  public void DiscoverServices() { 
//    String functionName = "DiscoverServices";
//
//    // on the connection, runs services
//    if (mConnectionState == STATE_CONNECTED) { 
//      boolean discoverStatus = mBluetoothGatt.discoverServices();
//      mLogMessage = "Discover Services, status: " + discoverStatus;
//      Log.i(LOG_TAG, "Starting Discover services flag");
//    } else { 
//      mLogMessage = "Trying to discover services, but device is not connected";
//      Log.w(LOG_TAG, mLogMessage);
//
//    }
//  }

  /**
   * Allows the Connect to Device
   */
  @SimpleFunction(description = "Starts GATT connection")
  public void Connect(String name) { 
    String functionName = "Connect";
    BluetoothDevice tempDevice;
    String testname;
    boolean foundDevice = false;

    // Search through strings and find matching one
    for (int loop1 = 0; loop1 < numDevices; loop1++) {
      // recover next device in list
      tempDevice = mLeDevices.get(loop1); 
      testname = tempDevice.getName() + ":" + tempDevice.toString();
  
      // check if this is the device
      if (testname.equals(name)) { 
        mDevice = tempDevice;
        foundDevice = true;
      }
    }

    // Fire off the callback
    if (foundDevice && (mConnectionState == STATE_DISCONNECTED)) { 
      mBluetoothGatt = mDevice.connectGatt(activity, false, mGattCallback);
      LogMessage("Connecting device " + mDevice.getName() + ":" + mDevice.toString(), "i");
    } else { 
      LogMessage("Trying to connected without a found device", "e");
    }
  }

  /**
   * Returns if Sensors are enabled
   *
   */
  @SimpleProperty(description = "Checks if Sensors are enabled", 
                  category = PropertyCategory.BEHAVIOR,
                  userVisible = true)
  public boolean SensorsEnabled() {
    return mSensorsEnabled;
  }

  /**
   * Turns on sensors
   *
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
      defaultValue = "False")
  @SimpleProperty(description = "Checks if Sensors are enabled", 
                  category = PropertyCategory.BEHAVIOR,
                  userVisible = true)
  public void SensorsEnabled(boolean enableFlag) {

    // turn on sensors
    if (enableFlag && !mSensorsEnabled) { 
      mSensorsEnabled = true;
    } else if (!enableFlag && mSensorsEnabled) {
      mSensorsEnabled = false;
    }

    // Fire off characteristic 
    if (validWICEDDevice) { 
      mBluetoothGatt.setCharacteristicNotification(mSensorNotification, mSensorsEnabled);
      if (mSensorsEnabled) { 
        LogMessage("Turning on Sensor notifications", "i");
      } else { 
        LogMessage("Turning off Sensor notifications", "i");
        mLogMessage = "Turning off Sensor notifications";
      }
    }
  }

  /**
   * Returns the scanning status
   *
   * @return scanning if still scanning
   */
  @SimpleProperty(description = "Checks if BTLE device is scanning", 
                  category = PropertyCategory.BEHAVIOR,
                  userVisible = true)
  public boolean Scanning() {
    return scanning;
  }


  /**
   * Acceleremeter sensor data
   */
  @SimpleProperty(description = "Gets Sensor data", category = PropertyCategory.BEHAVIOR, userVisible = true)
  public String XAccel() {
    return mXAccel;
  }
  @SimpleProperty(description = "Gets Sensor data", category = PropertyCategory.BEHAVIOR, userVisible = true)
  public String YAccel() {
    return mYAccel;
  }
  @SimpleProperty(description = "Gets Sensor data", category = PropertyCategory.BEHAVIOR, userVisible = true)
  public String ZAccel() {
    return mZAccel;
  }

  /**
   * Sets is enabled
   *
   * @return Checks if the BTLE is enabled
   */
  @SimpleProperty(description = "Checks if BTLE is available and enabled", 
                  category = PropertyCategory.BEHAVIOR,
                  userVisible = true)
  public boolean Enabled() {
    return isEnabled;
  }

  /**
   * Returns a list of the Gatt services
   */
  @SimpleProperty(description = "Lists the BLTE GATT Services", category = PropertyCategory.BEHAVIOR)
  public List<String> DeviceServices() { 
    List<String> listOfServices = new ArrayList<String>();
    int numServices;
    BluetoothGattService mService;

    // number of services discovered
    numServices = mGattServices.size();

    // bail out if nothing found
    if (numServices == 0) { 
      listOfServices.add("No Services Found");
      LogMessage("Did not find any Services", "i");
    } else { 
      LogMessage("Found " + numServices + " services", "i");
      for (int loop1 = 0; loop1 < numServices; loop1++) {
        mService = mGattServices.get(loop1);
        if (mService != null) { 
          listOfServices.add(mService.getUuid().toString());
        }
      }
    }
  
    return listOfServices;
  }

  /**
   * Allows to access a list of Devices found in the Scan
   */
  @SimpleProperty(description = "Lists the BLTE devices", category = PropertyCategory.BEHAVIOR)
  public List<String> AddressesAndNames() { 
    List<String> listOfBTLEDevices = new ArrayList<String>();
    String deviceName;
    BluetoothDevice nextDevice;

    if (numDevices == 0) {
      listOfBTLEDevices.add("No devices found");
      LogMessage("Did not find any devices to connect", "i");
    } else { 
      for (int loop1 = 0; loop1 < numDevices; loop1++) {
        nextDevice = mLeDevices.get(loop1);
        if (nextDevice != null) { 
          deviceName = nextDevice.getName(); 
          listOfBTLEDevices.add(deviceName + ":" + nextDevice.toString());
        }
      }
    }

    return listOfBTLEDevices;
  }

  /**  URGENT -- missing all the onResume() onPause() onStop() methods to cleanup
   *   the connections during Life-cycle of app
   *
   *
   */

}
