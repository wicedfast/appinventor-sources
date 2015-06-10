// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Copyright 2014 - David Garrett - Broadcom Corporation
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

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
import android.bluetooth.BluetoothGattDescriptor;


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
import com.google.appinventor.components.runtime.util.ErrorMessages;
import com.google.appinventor.components.runtime.util.SdkLevel;
import com.google.appinventor.components.runtime.util.YailList;
import com.google.appinventor.components.runtime.EventDispatcher;

import android.os.Handler;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.UUID;

/**
 * The WICEDSense component connects to the BLTE device
 *
 * @author  David Garrett (not the violionist)
 */
@DesignerComponent(version = YaVersion.WICEDSENSE_COMPONENT_VERSION,
  //category = ComponentCategory.CONNECTIVITY,
  category = ComponentCategory.SENSORS,
  description = "The WICEDSense component is still experimental",
  nonVisible = true,
  iconName = "images/wicedSenseIcon.png")
@SimpleObject
@UsesPermissions(permissionNames =
  "android.permission.BLUETOOTH, " +
  "android.permission.BLUETOOTH_ADMIN")
public final class WICEDSense extends AndroidNonvisibleComponent
  implements Component, OnStopListener, OnResumeListener, OnPauseListener, Deleteable {

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
  private ArrayList<DeviceScanRecord> mScannedDevices;

  // holds sensors data
  private boolean mSensorsEnabled = false;

  // Gatt client pointer
  private BluetoothGatt mBluetoothGatt = null;

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
  private int mBatteryLevel = -1;

  // Holds time stamp data
  private long startTime = 0;
  private long currentTime = 0;
  private long tempCurrentTime = 0;

  // Holds the sensor data
  private float mXAccel = 0;
  private float mYAccel = 0;
  private float mZAccel = 0;
  private float mXGyro = 0;
  private float mYGyro = 0;
  private float mZGyro = 0;
  private float mXMagnetometer = 0;
  private float mYMagnetometer = 0;
  private float mZMagnetometer = 0;
  private float mHumidity = 0;
  private float mPressure = 0;
  private float mTemperature = 0;

  // set default temperature setting
  private boolean mUseFahrenheit = true;
  private boolean mRunInBackground = false;

  // Defines BTLE States
  private static final int STATE_DISCONNECTED = 0;
  private static final int STATE_NEED_SERVICES = 1;
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

  // Used to post events to the UI Thread
  private final Handler androidUIHandler;

  /**
   * Creates a new WICEDSense component.
   *
   * @param container the enclosing component
   */
  public WICEDSense (ComponentContainer container) {
    super(container.$form());
    activity = container.$context();
    androidUIHandler = new Handler();

    // names the function
    String functionName = "WICEDSense";

    // record the constructor time
    startTime  = System.nanoTime();
    currentTime  = startTime;
    tempCurrentTime  = startTime;

    // setup new list of devices
    mScannedDevices = new ArrayList<DeviceScanRecord>();

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
      isEnabled = true;
      LogMessage("Found the BTLE Device on platform", "i");
    }

    // register with the forms to that OnResume and OnNewIntent
    // messages get sent to this component
    form.registerForOnResume(this);
    form.registerForOnStop(this);
    //form.registerForOnNewIntent(this);
    form.registerForOnPause(this);

  }
  /** Get Device name */
  private String GetDeviceNameAndAddress(BluetoothDevice nextDevice) {
    String mName;
    if (nextDevice != null) {
      mName = nextDevice.getName() + ":" + nextDevice.toString();
    } else {
      mName = "Null device";
    }
    return mName;
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
    }
  }


  /** Log Messages */
  private void CleanupBTLEState() {

    mConnectionState = STATE_DISCONNECTED;

    // null out services
    mSensorService = null;
    mSensorNotification = null;
    mBatteryService = null;
    mBatteryCharacteristic = null;
    mGattServices.clear();
    validWICEDDevice = false;
    mScannedDevices.clear();

    LogMessage("Issuing a cleanup of the BTLE state", "i");
  }

  /** ----------------------------------------------------------------------
   *  BTLE Code Section
   *  ----------------------------------------------------------------------
   */

  /* Create Device list from scan */
  public class DeviceScanRecord implements Comparable<DeviceScanRecord> {
    private BluetoothDevice device = null;
    private int rssi = 0;
    private byte[] scanRecord = null;

    public DeviceScanRecord() {
      device = null;
      rssi = 0;
      scanRecord = null;
    }

    // set the container to scan results
    public void setRecord(final BluetoothDevice deviceVal, int rssiVal, byte[] scanRecordVal) {
      this.device = deviceVal;
      this.rssi = rssiVal;

      this.scanRecord = new byte[scanRecordVal.length];
      for (int loop1 = 0; loop1 < scanRecordVal.length; loop1++) {
        this.scanRecord[loop1] = scanRecordVal[loop1];
      }
    }

    // get the RSSI
    public int getRssi() {
      return rssi;
    }
    // get the device handle
    public BluetoothDevice getDevice() {
      return device;
    }
    // returns the scan record data
    public String getScanRecord() {
      return bytesToHex(scanRecord);
    }

    public int compareTo(DeviceScanRecord compareScan) {
      return compareScan.getRssi() - this.rssi;
    }
  }

  // Set the sensor state
  public void setSensorState() {

    // Fire off characteristic
    if (validWICEDDevice) {

      // Write the characteristic
      if (mSensorNotification == null) {
        LogMessage("Trying to set sensors notification with null pointer", "e");
      } else {
        BluetoothGattDescriptor mSensorNotificationClientConfig;

        // Update descriptor client config
        mSensorNotificationClientConfig = mSensorNotification.getDescriptor(CLIENT_CONFIG_UUID);
        if (mSensorNotificationClientConfig == null) {
          LogMessage("Cannot find sensor client descriptor, this device is not supported", "e");
          return;
        }

        // set values in the descriptor
        if (mSensorsEnabled) {
          mSensorNotificationClientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        } else {
          mSensorNotificationClientConfig.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        }

        // write the gatt descriptor
        mBluetoothGatt.writeDescriptor(mSensorNotificationClientConfig);
        if (mSensorsEnabled) {
          LogMessage("Turning on Sensor notifications", "i");
        } else {
          LogMessage("Turning off Sensor notifications", "i");
        }
      }
    }
  }



  /** create adaptor */
  public static BluetoothAdapter newBluetoothAdapter(Context context) {
    final BluetoothManager bluetoothManager =
      (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    return bluetoothManager.getAdapter();
  }

  /** Device scan callback. */
  private LeScanCallback mLeScanCallback = new LeScanCallback() {
      @Override
      public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {

        // Add new device
        DeviceScanRecord newDevice = new DeviceScanRecord();
        boolean foundNewDevice = true;

        // get the device record
        newDevice.setRecord(device, rssi, scanRecord);

        // make sure to ignore null devices
        if (device != null) {

          // Search through found devices and find matching one
          for (int loop1 = 0; loop1 < mScannedDevices.size(); loop1++) {
            DeviceScanRecord prevDevice;

            // see if we already know about this device
            prevDevice = mScannedDevices.get(loop1);
            if (device.equals(prevDevice.getDevice())) {
              foundNewDevice = false;
            }
          }
          if (foundNewDevice) {
            mScannedDevices.add(newDevice);
            LogMessage("Adding a BTLE device " + GetDeviceNameAndAddress(device) + " with rssi = " + rssi + " dBm to scan list", "i");
            // Trigger Event
            FoundDevice(AddressesAndNames());
          }
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
        LogMessage("onConnectionStateChange callback with status = " + status, "i");

        //String intentAction;
        if (newState == BluetoothProfile.STATE_CONNECTED) {
          mConnectionState = STATE_NEED_SERVICES;

          // Trigger device discovery
          LogMessage("Connected to BLTE device, starting service discovery", "i");
          boolean success = mBluetoothGatt.discoverServices();
          if (!success) {
            LogMessage("Cannot start service discovery for some reason", "e");
          }
          // Finalizing the disconnect profile
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
          mConnectionState = STATE_DISCONNECTED;

          // close out connection
//             mBluetoothGatt.close();

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
        LogMessage("onReadRemoteRssi callback with status = " + status, "i");

        deviceRssi = rssi;
        LogMessage("Updating RSSI from remove device = " + rssi + " dBm", "i");

        // update RSSI
        //RSSIUpdated();
      }

      @Override
      // New services discovered
      public void onServicesDiscovered(BluetoothGatt gatt, int status) {

        LogMessage("onServicesDiscovered callback with status = " + status, "i");

        if (status == BluetoothGatt.GATT_SUCCESS) {
          // record services
          mGattServices = gatt.getServices();
          validWICEDDevice = true;

          // update connection state
          if (mConnectionState == STATE_NEED_SERVICES) {
            mConnectionState = STATE_CONNECTED;
          }

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
            LogMessage("Found services on WICED Sense device", "i");
          } else {
            LogMessage("Connected device is not a WICED Sense kit", "e");
          }

          // Set the sensor state directly
          setSensorState();

          // Triggers callback for connected device
          //Connected();
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
              mBatteryLevel = characteristic.getIntValue(
                BluetoothGattCharacteristic.FORMAT_UINT8, 0);
              LogMessage("Read battery level " + mBatteryLevel + "%", "i");
              // Trigger Event
              BatteryLevelUpdated(mBatteryLevel);
            } catch (Exception e) {
              LogMessage("Unable to read battery level.", "e");
              return;
            }
          }
        } else {
          LogMessage("Failure in reading Gatt Characteristics", "e");
        }
      }

      @Override
      public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {

        LogMessage("onDescriptorWrite with status = " + status, "i");
        if (mSensorNotification == null) {
          LogMessage("onDescriptorWrite: mSensorNotification == null", "e");
          return;
        }

        // set the enable value
        boolean success = mBluetoothGatt.setCharacteristicNotification(mSensorNotification, mSensorsEnabled);
        if (success) {
          LogMessage("Was able to write sensor notification characteristics", "i");
        } else {
          LogMessage("Failed to write sensor notification characteristic", "e");
        }
      }

      @Override
      public void onCharacteristicChanged(BluetoothGatt gatt,
        BluetoothGattCharacteristic characteristic) {
        if (SENSOR_NOTIFICATION_UUID.equals(characteristic.getUuid())) {
          byte[] value = characteristic.getValue();
          int bitMask = value[0];
          int index = 1;

          // Update timestamp
          currentTime = System.nanoTime();

          if ((bitMask & 0x1)>0) {
            mXAccel = (value[index+1] << 8) + (value[  index] & 0xFF);
            mYAccel = (value[index+3] << 8) + (value[index+2] & 0xFF);
            mZAccel = (value[index+5] << 8) + (value[index+4] & 0xFF);
            index = index + 6;
          }
          if ((bitMask & 0x2)>0) {
            mXGyro = (value[index+1] << 8) + (value[  index] & 0xFF);
            mYGyro = (value[index+3] << 8) + (value[index+2] & 0xFF);
            mZGyro = (value[index+5] << 8) + (value[index+4] & 0xFF);
            mXGyro = mXGyro / (float)100.0;
            mYGyro = mYGyro / (float)100.0;
            mZGyro = mZGyro / (float)100.0;
            index = index + 6;
          }
          if ((bitMask & 0x4)>0) {
            mHumidity =  ((value[index+1] & 0xFF) << 8) + (value[index] & 0xFF);
            mHumidity = mHumidity / (float)10.0;
            index = index + 2;
          }
          if ((bitMask & 0x8)>0) {
            mXMagnetometer = (value[index+1] << 8) + (value[  index] & 0xFF);
            mYMagnetometer = (value[index+3] << 8) + (value[index+2] & 0xFF);
            mZMagnetometer = (value[index+5] << 8) + (value[index+4] & 0xFF);
            index = index + 6;
          }
          if ((bitMask & 0x10)>0) {
            mPressure =  ((value[index+1] & 0xFF) << 8) + (value[index] & 0xFF);
            mPressure = mPressure / (float)10.0;
            index = index + 2;
          }
          if ((bitMask & 0x20)>0) {
            mTemperature =  ((value[index+1] & 0xFF) << 8) + (value[index] & 0xFF);
            mTemperature = mTemperature / (float)10.0;
            index = index + 2;
            tempCurrentTime = System.nanoTime();
          }

          LogMessage("Reading back sensor data with type " + bitMask + " packet", "i");
          //SensorsUpdated();
        }
      }
    };


  /* ----------------------------------------------------------------------
   * ----------------------------------------------------------------------
   * GUI Interface Code Section
   * ----------------------------------------------------------------------
   * ----------------------------------------------------------------------
   */

  /*  ----------------------------------------------------------------------
   *   Events
   *   ----------------------------------------------------------------------
   */

  /**
   * Callback for an Error Event
   */
//  @SimpleEvent(description = "Event when there is an Error.")
//  public void Error() {
//    LogMessage("Firing the Error()", "e");
//    EventDispatcher.dispatchEvent(this, "Error");
//  }

  /**
   * Callback for Found Device Event
   */
  @SimpleEvent(description = "Event when an LE Device is found in scan.")
  public void FoundDevice(final YailList returnList) {
    LogMessage("Firing the FoundDevice() event", "i");
    androidUIHandler.post(new Runnable() {
        public void run() {
          EventDispatcher.dispatchEvent(WICEDSense.this, "FoundDevice", returnList);
        }
      });
  }

  /**
   * Callback for RSSI data
   */
  @SimpleEvent(description = "RSSI Read Event.")
  public void RSSIUpdated() {
    boolean success;
    LogMessage("Firing the RSSIUpdated() event", "i");
    success = EventDispatcher.dispatchEvent(this, "RSSIUpdated");
    if (!success) {
      LogMessage("Failed to dispatch RSSIUpdated() event", "e");
    } else {
      LogMessage("Success in dispatching RSSIUpdated() event", "i");
    }
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
   * Callback events for battery levels
   */
  @SimpleEvent(description = "Received Battery Level.")
  public void BatteryLevelUpdated(final int BatteryLevel) {
    androidUIHandler.post(new Runnable() {
        public void run() {
          EventDispatcher.dispatchEvent(WICEDSense.this, "BatteryLevelUpdated", BatteryLevel);
        }
      });
  }

  /**  ----------------------------------------------------------------------
   *   Function calls
   *   ----------------------------------------------------------------------
   */

  /**
   * Allows the user to check battery level
   */
  @SimpleFunction(description = "Reads WICED Sense kit battery level.")
  public void ReadBatteryLevel() {
    String functionName = "ReadBatteryLevel";
    if (mConnectionState == STATE_CONNECTED) {
      if (validWICEDDevice) {
        if (mBatteryCharacteristic == null) {
          LogMessage("Reading null battery characteristic", "e");
        } else {
          boolean success = mBluetoothGatt.readCharacteristic(mBatteryCharacteristic);
          if (success) {
            LogMessage("Reading battery characteristic", "i");
          } else {
            LogMessage("Reading battery characteristic failed", "e");
          }
        }
      } else {
        LogMessage("Trying to reading battery without a WICED Sense device", "e");
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
      mScannedDevices.clear();
      scanning = true;

      // Force the LE scan
      try {
        bluetoothAdapter.startLeScan(mLeScanCallback);
        LogMessage("Starting LE scan", "i");
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
        LogMessage("Stopping LE scan with " + mScannedDevices.size() + " devices", "i");

        if (mScannedDevices.size() > 0) {
          // Sort the list of devices by RSSI
          Collections.sort(mScannedDevices);
        }

      } catch (Exception e) {
        LogMessage("Failed to stop LE scan", "e");
      }
    }
  }

  /**  ----------------------------------------------------------------------
   *   Properties of the Device
   *   ----------------------------------------------------------------------
   */

  /** Checks we have found services on the device */
  @SimpleProperty(description = "Queries if Device Services have been discoverd",
    category = PropertyCategory.BEHAVIOR,
    userVisible = true)
    public boolean FoundServices() {
    if (mConnectionState == STATE_CONNECTED) {
      return true;
    } else {
      return false;
    }
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
//  @SimpleProperty(description = "Queries current log message",
//                  category = PropertyCategory.BEHAVIOR,
//                  userVisible = true)
//  public String Text() {
//    return mLogMessage;
//  }

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

    if (mConnectionState == STATE_CONNECTED || mConnectionState == STATE_NEED_SERVICES) {
      mBluetoothGatt.disconnect();
      LogMessage("Disconnecting from device", "i");
    } else {
      LogMessage("Trying to disconnect without a connected device", "e");
    }
  }

  /**
   * Resets the internal counter
   */
  @SimpleFunction(description = "Resets the internal timer")
  public void ResetTimestamp() {
    startTime = System.nanoTime();
    currentTime = startTime;
    tempCurrentTime = startTime;
  }

  /**
   * Allows to Connect to closest Device
   */
  @SimpleFunction(description = "Connects to the WICED sense kit with the strongest RSSI")
  public void ConnectClosest() {
    String functionName = "ConnectClosest";
    DeviceScanRecord nextScannedDevice;
    String testname;
    boolean foundDevice = false;
    int maxRssi = -160;

    // check connected state
    if (mConnectionState == STATE_DISCONNECTED) {

      // log message
      LogMessage("Testing " + mScannedDevices.size() + " device(s) for closest scanned BTLE device", "i");

      // Search through strings and find matching one
      for (int loop1 = 0; loop1 < mScannedDevices.size(); loop1++) {

        // get the next device
        nextScannedDevice = mScannedDevices.get(loop1);
        LogMessage("Testing device " + GetDeviceNameAndAddress(nextScannedDevice.getDevice()) + ", rssi = " + nextScannedDevice.getRssi() + " dBm", "i");

        // update maximum value
        if (nextScannedDevice.getRssi() > maxRssi) {
          maxRssi = nextScannedDevice.getRssi();
          // setup device name
          mDevice = nextScannedDevice.getDevice();
          //tempDevice = nextScannedDevice.getDevice();
          testname = GetDeviceNameAndAddress(mDevice);
          LogMessage("Found closest device " + testname + ", rssi = " + maxRssi + " dBm", "i");
          foundDevice = true;
        }
      }

      // Found the best device to connect
      if (foundDevice) {
        mBluetoothGatt = mDevice.connectGatt(activity, false, mGattCallback);
        LogMessage("Connecting device " + GetDeviceNameAndAddress(mDevice), "i");
      } else {
        LogMessage("No device found to connect", "e");
      }
    } else {
      LogMessage("Trying to connect with an already connected device", "e");
    }
  }

  /**
   * Allows the Connect to Device
   */
  @SimpleFunction(description = "Connects to the named WICED Sense kit")
  public void Connect(String name) {
    String functionName = "Connect";
    DeviceScanRecord nextScanRecord;
    BluetoothDevice tempDevice;
    String testname;
    boolean foundDevice = false;

    if (mConnectionState == STATE_DISCONNECTED) {

      // Search through strings and find matching one
      for (int loop1 = 0; loop1 < mScannedDevices.size(); loop1++) {
        // recover next device in list
        tempDevice = mScannedDevices.get(loop1).getDevice();
        testname = GetDeviceNameAndAddress(tempDevice);

        // check if this is the device
        if (testname.equals(name)) {
          mDevice = tempDevice;
          foundDevice = true;
        }
      }

      // Fire off the callback
      if (foundDevice) {
        mBluetoothGatt = mDevice.connectGatt(activity, false, mGattCallback);
        LogMessage("Connecting device " + GetDeviceNameAndAddress(mDevice), "i");
      } else {
        LogMessage("No device found to connect", "e");
      }
    } else {
      LogMessage("Trying to connect with an already connected device", "e");
    }
  }

  /**
   * Returns the time since reset in milliseconds
   *
   */
  @SimpleProperty(description = "Returns timestamp of sensor data in milliseconds since reset",
    category = PropertyCategory.BEHAVIOR,
    userVisible = true)
    public int Timestamp() {
    long timeDiff;
    int timeMilliseconds;

    // compute nanoseconds since start time
    timeDiff = currentTime - startTime;

    // Convert to milliseconds
    timeDiff = timeDiff / 1000000;

    // convert to int
    timeMilliseconds = (int)timeDiff;

    return timeMilliseconds;
  }

  /**
   * Returns the time since reset in milliseconds
   *
   */
  @SimpleProperty(description = "Returns timestamp of just temperature, humidity and pressure sensor data in milliseconds since reset",
    category = PropertyCategory.BEHAVIOR,
    userVisible = true)
    public int TemperatureTimestamp() {
    long timeDiff;
    int timeMilliseconds;

    // compute nanoseconds since start time
    timeDiff = tempCurrentTime - startTime;

    // Convert to milliseconds
    timeDiff = timeDiff / 1000000;

    // convert to int
    timeMilliseconds = (int)timeDiff;

    return timeMilliseconds;
  }

  /**
   * Sets the temperature setting for Fahrenheit or Celsius
   *
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
    defaultValue = "True")
    @SimpleProperty(description = "Keeps BTLE running in background",
      category = PropertyCategory.BEHAVIOR,
      userVisible = true)
      public void RunInBackground(boolean enableFlag) {
    mRunInBackground = enableFlag;
  }

  /**
   * Sets the temperature setting for Fahrenheit or Celsius
   *
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
    defaultValue = "True")
    @SimpleProperty(description = "Sets temperature data in Fahrenheit, not Celius",
      category = PropertyCategory.BEHAVIOR,
      userVisible = true)
      public void UseFahrenheit(boolean enableFlag) {
    mUseFahrenheit = enableFlag;
  }

  /**
   * Sets the temperature setting for Fahrenheit or Celsius
   *
   */
  @SimpleProperty(description = "Returns true if temperature format is in Fahrenheit",
    category = PropertyCategory.BEHAVIOR,
    userVisible = true)
    public boolean UseFahrenheit() {
    return mUseFahrenheit;
  }

  /**
   * Returns if Sensors are enabled
   *
   */
  @SimpleProperty(description = "Returns true if Sensors are enabled",
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
    @SimpleProperty(description = "Sets the sensor enabled flag",
      category = PropertyCategory.BEHAVIOR,
      userVisible = true)
      public void SensorsEnabled(boolean enableFlag) {

    mSensorsEnabled = enableFlag;
    if (enableFlag) {
      LogMessage("Setting SensorsEnabled to true", "i");
    } else {
      LogMessage("Setting SensorsEnabled to false", "i");
    }

    // Transfer to device if it's connected
    setSensorState();

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
   * Return the X Accelerometer sensor data
   */
  @SimpleProperty(description = "Get X Accelerometer data", category = PropertyCategory.BEHAVIOR, userVisible = true)
  public float XAccel() {
    return mXAccel;
  }

  /**
   * Return the Y Accelerometer sensor data
   */
  @SimpleProperty(description = "Get Y Accelerometer data", category = PropertyCategory.BEHAVIOR, userVisible = true)
  public float YAccel() {
    return mYAccel;
  }

  /**
   * Return the Z Accelerometer sensor data
   */
  @SimpleProperty(description = "Get Z Accelerometer data", category = PropertyCategory.BEHAVIOR, userVisible = true)
  public float ZAccel() {
    return mZAccel;
  }

  /**
   * Return the X Gyro sensor data
   */
  @SimpleProperty(description = "Get X Gyro data", category = PropertyCategory.BEHAVIOR, userVisible = true)
  public float XGyro() {
    return mXGyro;
  }

  /**
   * Return the Y Gyro sensor data
   */
  @SimpleProperty(description = "Get Y Gyro data", category = PropertyCategory.BEHAVIOR, userVisible = true)
  public float YGyro() {
    return mYGyro;
  }

  /**
   * Return the Z Gyro sensor data
   */
  @SimpleProperty(description = "Get Z Gyro data", category = PropertyCategory.BEHAVIOR, userVisible = true)
  public float ZGyro() {
    return mZGyro;
  }


  /**
   * Return the X Magnetometer sensor data
   */
  @SimpleProperty(description = "Get X Magnetometer data", category = PropertyCategory.BEHAVIOR, userVisible = true)
  public float XMagnetometer() {
    return mXMagnetometer;
  }

  /**
   * Return the Y Magnetometer sensor data
   */
  @SimpleProperty(description = "Get Y Magnetometer data", category = PropertyCategory.BEHAVIOR, userVisible = true)
  public float YMagnetometer() {
    return mYMagnetometer;
  }

  /**
   * Return the Z Magnetometer sensor data
   */
  @SimpleProperty(description = "Get Z Magnetometer data", category = PropertyCategory.BEHAVIOR, userVisible = true)
  public float ZMagnetometer() {
    return mZMagnetometer;
  }

  /**
   * Return the Compass heading
   */
  @SimpleProperty(description = "Get the compass heading in degrees assuming device is flat", category = PropertyCategory.BEHAVIOR, userVisible = true)
  public float Heading() {
    double mag = Math.sqrt(mXMagnetometer*mXMagnetometer + mYMagnetometer*mYMagnetometer);
    double heading;

    LogMessage("Calculating heading from X+Y magnetometer data (" +
      mXMagnetometer + "," + mYMagnetometer + "), mag = " + mag, "i");

    if (mag > 0.0) {
      // convert x,y to radians to degrees
      double nX = mXMagnetometer/mag;
      double nY = mYMagnetometer/mag;
      heading = Math.atan2(nY, nX) * 57.295779578 + 180.0;
    } else {
      heading = 0.0;
    }

    LogMessage("Heading = " + heading, "i");
    return (float)heading;
  }

  /**
   * Return the Humidity sensor data
   */
  @SimpleProperty(description = "Get Humidity data in %",
    category = PropertyCategory.BEHAVIOR,
    userVisible = true)
    public float Humidity() {
    return mHumidity;
  }

  /**
   * Return the Pressure sensor data
   */
  @SimpleProperty(description = "Get Pressure data in millibar",
    category = PropertyCategory.BEHAVIOR,
    userVisible = true)
    public float Pressure() {
    return mPressure;
  }

  /**
   * Return the Temperature sensor data
   */
  @SimpleProperty(description = "Get Temperature data in Fahrenheit or Celsius",
    category = PropertyCategory.BEHAVIOR,
    userVisible = true)
    public float Temperature() {
    float tempConvert;

    // get temperature in celsius
    tempConvert = mTemperature;

    // Convert to Fahrenheit if selected
    if (mUseFahrenheit) {
      tempConvert = tempConvert* (float)(9.0/5.0) + (float)32.0;
    }

    return tempConvert;
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
   * Allows to access of scan records found in the Scan
   */
  @SimpleProperty(description = "Lists the scan record of all BLTE devices find in scan", category = PropertyCategory.BEHAVIOR)
  public List<String> ScanRecords() {
    List<String> listOfScanRecords = new ArrayList<String>();
    BluetoothDevice nextDevice;

    if (mScannedDevices.size() == 0) {
      LogMessage("Did not find any devices in scan", "i");
    } else {
      for (int loop1 = 0; loop1 < mScannedDevices.size(); loop1++) {
        nextDevice = mScannedDevices.get(loop1).getDevice();
        if (nextDevice != null) {
          listOfScanRecords.add(mScannedDevices.get(loop1).getScanRecord());
          LogMessage("Adding scan record to list: " + mScannedDevices.get(loop1).getScanRecord(), "i");
        }
      }
    }

    return listOfScanRecords;
  }

  /**
   * Allows to access of RSSI found in scan
   */
  @SimpleProperty(description = "Lists the RSSI of all BLTE devices find in scan", category = PropertyCategory.BEHAVIOR)
  public List<Integer> ScanRSSI() {
    List<Integer> listOfRSSI = new ArrayList<Integer>();
    BluetoothDevice nextDevice;

    if (mScannedDevices.size() == 0) {
      LogMessage("Did not find any devices in scan", "i");
    } else {
      for (int loop1 = 0; loop1 < mScannedDevices.size(); loop1++) {
        nextDevice = mScannedDevices.get(loop1).getDevice();
        if (nextDevice != null) {
          listOfRSSI.add(mScannedDevices.get(loop1).getRssi());
          LogMessage("Adding scan RSSI to list: " + mScannedDevices.get(loop1).getRssi(), "i");
        }
      }
    }

    return listOfRSSI;
  }

  /**
   * Allows to access a list of Devices found in the Scan
   */
  @SimpleProperty(description = "Lists the BLTE devices", category = PropertyCategory.BEHAVIOR)
  public YailList AddressesAndNames() {
    List<String> listOfBTLEDevices = new ArrayList<String>();
    String deviceName;
    BluetoothDevice nextDevice;
    int foundCount = 0;

    LogMessage("Finding names in " + mScannedDevices.size() + " devices", "i");
    for (int i = 0; i < mScannedDevices.size(); i++) {
      nextDevice = mScannedDevices.get(i).getDevice();
      if (nextDevice != null) {
        deviceName = GetDeviceNameAndAddress(nextDevice);
        listOfBTLEDevices.add(deviceName);
      }
    }
    return YailList.makeList(listOfBTLEDevices);
  }

  /**  URGENT -- missing all the onResume() onPause() onStop() methods to cleanup
   *   the connections during Life-cycle of app
   *
   *
   */

  //
  public void onResume() {
    LogMessage("Resuming the WICED Sense component", "i");
  }

  public void onPause() {
    LogMessage("Calling onPause()", "i");
  }

  @Override
  public void onDelete() {
    LogMessage("Deleting the WICED Sense component", "i");
    if (mBluetoothGatt != null) {
      mBluetoothGatt.close();
    }
  }

  @Override
  public void onStop() {
    LogMessage("Calling onStop()", "i");

    // Force a disconnect on Stop
    if (mRunInBackground) {
      LogMessage("Continuing to run in the background", "i");
    } else {
      LogMessage("Auto-disconnecting device from onStop()", "i");
      Disconnect();
    }

  }

}
