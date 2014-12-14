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
  
  // Set to 1 during scanning
  private boolean scanning = true;

  // Holds the link to the Bluetooth Adapter
  private BluetoothAdapter bluetoothAdapter;

  // holds error message
  private String errorMessage = "Initial State";

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
  private BluetoothGattService mSensorService = null;
  private BluetoothGattCharacteristic mSensorNotification = null;

  private BluetoothGattService mBatteryService = null;
  private BluetoothGattCharacteristic mBatteryLevel = null;

  // Holds Battery level
  private int batteryLevel = -1;
  
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
      Log.e(LOG_TAG, "No valid BTLE Device");

      /** issues message to reader */
      form.dispatchErrorOccurredEvent(this, "WICEDSense",
          ErrorMessages.ERROR_BLUETOOTH_NOT_ENABLED);

    } else { 
      Log.i(LOG_TAG, "Found valid BTLE Device");
      isEnabled = true;
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
      Log.i(LOG_TAG, "Got to the onLeScan callback");

      // Record the first device you find
      if (!mLeDevices.contains(device)) { 
        mLeDevices.add(device);
        numDevices++;
      }
    }
  };

  /** Various callback methods defined by the BLE API. */
  private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            //String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnectionState = STATE_CONNECTED;
                errorMessage = "Connected callback worked";
                Log.i(LOG_TAG, "Connected to GATT server.");

                //Trigger connected event
                ConnectedEvent();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mConnectionState = STATE_DISCONNECTED;
                errorMessage = "Disconnected";
                Log.i(LOG_TAG, "Disconnected from GATT server.");
            }
        }

        @Override
        // New services discovered
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
          deviceRssi = rssi;
          Log.i(LOG_TAG, "Updating RSSI " + status);
        }
        
        @Override
        // New services discovered
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
          if (status == BluetoothGatt.GATT_SUCCESS) {
            // record services 
            errorMessage = "Found new services";
            mGattServices = gatt.getServices();

            // Match to sensor services
            BluetoothGattService mService;
            for (int loop1 = 0; loop1 < mGattServices.size(); loop1++) {
              mService = mGattServices.get(loop1);
              // get battery service
              if (BATTERY_SERVICE_UUID.equals(mService.getUuid())) { 
                errorMessage = "Found BATTERY SERVICE";
                mBatteryService = mService;
                mBatteryLevel = mBatteryService.getCharacteristic(BATTERY_LEVEL_UUID);
              } 
              // get the sensor service
              if (SENSOR_SERVICE_UUID.equals(mService.getUuid())) { 
                mSensorService = mService;
                mSensorNotification = mSensorService.getCharacteristic(SENSOR_NOTIFICATION_UUID);
              } 
            }
            Log.i(LOG_TAG, "Found the services on BTLE device");

            // turn on notifications if needed
            SensorsEnabled(mSensorsEnabled);

            // Triggers callback
            ServicesFoundEvent();
          } else {
            Log.w(LOG_TAG, "onServicesDiscovered received: " + status);
            errorMessage = "Found new services, but status = " + status;
          }
        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt, 
                                         BluetoothGattCharacteristic characteristic, 
                                         int status) {
          if (status == BluetoothGatt.GATT_SUCCESS) {
            errorMessage = "Success read characterstics";

            // Check for battery level
            if (BATTERY_LEVEL_UUID.equals(characteristic.getUuid())) {
              try {
                batteryLevel = characteristic.getIntValue(
                               BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                // trigger event
                BatteryLevelEvent();
              } catch (Throwable t) {
                Log.e(LOG_TAG, "Unable to read battery level", t);
                return;
              }
            }
          } else {
            errorMessage = "Failing in reading Gatt Characteristics";
          }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                         BluetoothGattCharacteristic characteristic) {
          if (SENSOR_NOTIFICATION_UUID.equals(characteristic.getUuid())) {
            byte[] value = characteristic.getValue();
            errorMessage = "Reading back Sensor data: " + value[0] + " with length";
            
           // if (mEventCallback != null) {
           //     mEventCallback.onSensorData(this, value);
           // }

            // Set notification     
            SensorsEvent();
          }
        }

   };


  /** ----------------------------------------------------------------------
   *  GUI Interface Code Section
   *  ----------------------------------------------------------------------
   */
 
  /**
   * Callback events for device connection
   */
  @SimpleEvent(description = "BTLE Connected Event.")
  public void ConnectedEvent() { 
    errorMessage = "Found Connected Event";
    EventDispatcher.dispatchEvent(this, "ConnectedEvent");
  }

  /**
   * Callback events for Sensor Update
   */
  @SimpleEvent(description = "Sensor data updated.")
  public void SensorsEvent() { 
    errorMessage = "Sensors updated";
    EventDispatcher.dispatchEvent(this, "SensorsEvent");
  }

  /**
   * Callback events for device connection
   */
  @SimpleEvent(description = "Discovered Services Event.")
  public void ServicesFoundEvent() { 
    errorMessage = "Service Found Connected Event";
    EventDispatcher.dispatchEvent(this, "ServicesFoundEvent");
  }

  /**
   * Callback events for batery levels
   */
  @SimpleEvent(description = "Received Battery Level.")
  public void BatteryLevelEvent() { 
    errorMessage = "Battery level event = " + batteryLevel;
    EventDispatcher.dispatchEvent(this, "BatteryLevelEvent");
  }

  /**
   * Allows the user to check battery level
   */
  @SimpleFunction(description = "Starts BTLE scanning")
  public void ReadBatteryLevel() { 
    String functionName = "ReadBatteryLevel";
    if (mConnectionState == STATE_CONNECTED) { 
      if (mBatteryService != null) { 
        mBluetoothGatt.readCharacteristic(mBatteryLevel);
        errorMessage = "Read battery characteristic";
      } else { 
        errorMessage = "Trying to read battery characteristic before discovery";
      }
    } else { 
      errorMessage = "Read battery, but not connection";
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
        Log.e(LOG_TAG, "Failed to start scale " + e.getMessage());
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
        Log.e(LOG_TAG, "Failed to stop scanning " + e.getMessage());
      }
    }
  }

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
    if (mConnectionState == STATE_CONNECTED) { 
      mBluetoothGatt.readRemoteRssi();
      Log.i(LOG_TAG, "Starting read of RSSI");
    }
    return deviceRssi;
  }


  /**
   * Returns text log
   */
  @SimpleProperty(description = "Queries Text", 
                  category = PropertyCategory.BEHAVIOR,
                  userVisible = true)
  public String Text() {
    Log.i(LOG_TAG, "Returning " + errorMessage);
    return errorMessage;
  }

  /**
   * Allows the user to disconnect
   */
  @SimpleFunction(description = "Disconnects GATT connection")
  public void Disconnect() { 
    String functionName = "Disconnect";

    if (mConnectionState == STATE_CONNECTED) { 
      mBluetoothGatt.disconnect();
      Log.i(LOG_TAG, "Disconnected device");
    } else { 
      Log.e(LOG_TAG, "Trying to disconnect without a valid Gatt");
    }
  }

  /**
   * Sets up device discovery
   */
  @SimpleFunction(description = "Initiates a service discovery")
  public void DiscoverServices() { 
    String functionName = "DiscoverServices";

    // on the connection, runs services
    if (mConnectionState == STATE_CONNECTED) { 
      boolean discoverStatus = mBluetoothGatt.discoverServices();
      errorMessage = "Discover Services, status: " + discoverStatus;
      Log.i(LOG_TAG, "Starting Discover services flag");
    } else { 
      errorMessage = "Trying to discover services, but device is not connected";
      Log.w(LOG_TAG, errorMessage);

    }
  }

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
      errorMessage = "Connecting device " + mDevice.getName() + ":" + mDevice.toString();
      Log.i(LOG_TAG, "Connecting to device");
    } else { 
      errorMessage = "Trying to connect to " + name;
      Log.e(LOG_TAG, "Trying to connected without a found device");
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
    if (mConnectionState == STATE_CONNECTED) { 
      if (mSensorNotification != null) { 
        mBluetoothGatt.setCharacteristicNotification(mSensorNotification, mSensorsEnabled);
        if (mSensorsEnabled) { 
          errorMessage = "Turning on Sensor notifications";
        } else { 
          errorMessage = "Turning off Sensor notifications";
        }
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
      Log.i(LOG_TAG, "Did not find any services");
    } else { 
      errorMessage = "Adding " + numServices + " service to list";
      for (int loop1 = 0; loop1 < numServices; loop1++) {
        mService = mGattServices.get(loop1);
        if (mService != null) { 
          listOfServices.add("service = " + mService.getUuid().toString());
        } else { 
          listOfServices.add("Null service");
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
      Log.i(LOG_TAG, "Did not find any devices to connect");
    } else { 
      for (int loop1 = 0; loop1 < numDevices; loop1++) {
        nextDevice = mLeDevices.get(loop1);
        if (nextDevice != null) { 
          deviceName = nextDevice.getName(); 
          listOfBTLEDevices.add(deviceName + ":" + nextDevice.toString());
        }
      }
      Log.i(LOG_TAG, "Returning name and addresses of devices");
    }

    return listOfBTLEDevices;
  }

  /**  URGENT -- missing all the onResume() onPause() onStop() methods to cleanup
   *   the connections during Life-cycle of app
   *
   *
   */

}
