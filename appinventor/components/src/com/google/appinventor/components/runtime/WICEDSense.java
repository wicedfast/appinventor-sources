// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.components.runtime;

import android.app.Activity;
//import android.app.AlertDialog;
import android.content.Context;
//import android.content.DialogInterface;
//import android.graphics.Color;
//import android.graphics.Typeface;
import android.os.Handler;
//import android.text.Html;
//import android.text.SpannableString;
import android.util.Log;
//import android.view.Gravity;
//import android.view.inputmethod.InputMethodManager;
//import android.widget.EditText;
//import android.widget.TextView;
//import android.widget.Toast;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothGattCallback;


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


import java.util.List;
import java.util.ArrayList;

/**
 * The WICEDSense component connects to the BLTE device
 *
 * @author  David Garrett (not the violionist)
 */

@DesignerComponent(version = YaVersion.NOTIFIER_COMPONENT_VERSION,
    category = ComponentCategory.CONNECTIVITY,
    description = "The WICEDSense component is experimental",
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

  private String errorMessage;

  // Holds list of devices
//  private LeDeviceListAdapter leDeviceListAdapter;

  // holds the BT device
  private boolean foundDevice = false;
  private int deviceRssi = -130;
  private BluetoothDevice mDevice;
  private int numDevices = 0;
  private ArrayList<BluetoothDevice> mLeDevices;

  // Gatt client pointer
  private BluetoothGatt mBluetoothGatt;

  private int mConnectionState = STATE_DISCONNECTED;

  private static final int STATE_DISCONNECTED = 0;
  private static final int STATE_CONNECTING = 1;
  private static final int STATE_CONNECTED = 2;

  /**
   * Creates a new WICEDSense component.
   *
   * @param container the enclosing component
   */
  public WICEDSense (ComponentContainer container) {
    super(container.$form());
    activity = container.$context();

    // setup new list of devices
    mLeDevices = new ArrayList<BluetoothDevice>();

    /* Setup the Bluetooth adapter */
    bluetoothAdapter = (SdkLevel.getLevel() >= SdkLevel.LEVEL_JELLYBEAN_MR2)
        ? newBluetoothAdapter(activity)
        : null;
    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) { 
      isEnabled = false;
      Log.e(LOG_TAG, "No valid BTLE Device");
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
      
//      if (!foundDevice) { 
//        foundDevice = true;
//        deviceRssi = rssi;
//        mDevice = device;
//      }
       // runOnUiThread(new Runnable() {
       //    @Override
       //    public void run() {
       //       leDeviceListAdapter.addDevice(device);
       //       // mLeDeviceListAdapter.notifyDataSetChanged();
       //     }
       //  });
    }
  };



//  /** Holds the callback list of devices */
//  private class LeDeviceListAdapter  {
//    private ArrayList<BluetoothDevice> mLeDevices;
//
//    public LeDeviceListAdapter() {
//      super();
//      mLeDevices = new ArrayList<BluetoothDevice>();
//    }
//    public void addDevice(BluetoothDevice device) {
//      if(!mLeDevices.contains(device)) {
//        Log.i(LOG_TAG, "Adding new BTLE device");
//        mLeDevices.add(device);
//      }
//    }
//    public BluetoothDevice getDevice(int position) {
//      return mLeDevices.get(position);
//    }
//    public void clear() {
//      mLeDevices.clear();
//    }
//    public int getCount() {
//      return mLeDevices.size();
//    }
//    public Object getItem(int i) {
//      return mLeDevices.get(i);
//    }
//  }

  /** Various callback methods defined by the BLE API. */
  private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            //String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                //intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                //broadcastUpdate(intentAction);
                Log.i(LOG_TAG, "Connected to GATT server.");
                //Log.i(LOG_TAG, "Attempting to start service discovery:" + mBluetoothGatt.discoverServices());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                //intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                //mConnectionState = STATE_DISCONNECTED;
                Log.i(LOG_TAG, "Disconnected from GATT server.");
                //broadcastUpdate(intentAction);
            }
        }

        @Override
        // New services discovered
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
          deviceRssi = rssi;
          Log.i(LOG_TAG, "Updating RSSI " + status);
        }
        
//        @Override
//        // New services discovered
//        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
//            } else {
//                Log.w(TAG, "onServicesDiscovered received: " + status);
//            }
//        }
//
//        @Override
//        // Result of a characteristic read operation
//        public void onCharacteristicRead(BluetoothGatt gatt,
//                BluetoothGattCharacteristic characteristic,
//                int status) {
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
//            }
//        }
    };


  /** ----------------------------------------------------------------------
   *  GUI Interface Code Section
   *  ----------------------------------------------------------------------
   */

  /**
   * Allows the user to start the scan
   */
  @SimpleFunction(description = "Starts BTLE scanning")
  public void startLeScan() { 
    String functionName = "startLeScan";
    if (!scanning) { 
      scanning = true;
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
   * Allows the user to Stop the scan
   */
  @SimpleFunction(description = "Starts GATT connection")
  public void Connect(String name) { 
    String functionName = "Connect";
    BluetoothDevice tempDevice;
    String testname;

    for (int loop1 = 0; loop1 < numDevices; loop1++) {
      // recover next device in list
      tempDevice = mLeDevices.get(loop1); 
      testname = tempDevice.getName() + ":" + tempDevice.toString();
  
      // check if this is the device
      if (testname == name) { 
        mDevice = tempDevice;
        foundDevice = true;
      }
    }

    if (foundDevice && (mConnectionState == STATE_DISCONNECTED)) { 
      mBluetoothGatt = mDevice.connectGatt(activity, false, mGattCallback);
      Log.i(LOG_TAG, "Connecting to device");
    } else { 
      Log.e(LOG_TAG, "Trying to connected without a found device");
    }
  }

  /**
   * Returns the scanning
   *
   * @return scanning enables scanning
   */
  @SimpleProperty(description = "Checks if BTLE device is scanning", category = PropertyCategory.BEHAVIOR,
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
   * Allows to access a list of Devices found in the Scan
   */
  @SimpleProperty(description = "Lists the BLTE devices", category = PropertyCategory.BEHAVIOR)
  public List<String> AddressesAndNames() { 
    List<String> listOfBTLEDevices = new ArrayList<String>();
    String deviceName;

    //deviceName = "number of devices = ";    
    //int numDevices;
    //numDevices = leDeviceListAdapter.getCount();

    // add the device
    if (foundDevice) {
      deviceName = mDevice.getName();
      listOfBTLEDevices.add(deviceName + ":" + mDevice.toString());
    } else { 
      listOfBTLEDevices.add("Did not find a device");
    }

    //listOfBTLEDevices.add("Dummy device");
    //for (int loop1 = 0; loop1 < numDevices; loop1++) {
    //}

    // get names
//    if (leDeviceListAdapter.getCount() == 0) { 
//      listOfBTLEDevices.add("No BTLE devices found");
//      Log.i(LOG_TAG, "Did not find any devices");
//    } else { 
//
//        try { 
//          BluetoothDevice mDevice = leDeviceListAdapter.getDevice(loop1);
//          // get list of names
//          deviceName = mDevice.getName();
//          listOfBTLEDevices.add(deviceName);
//        } catch (Exception e) { 
//          Log.e(LOG_TAG, "Failed to Lookup mDevice device" + e.getMessage());
//          listOfBTLEDevices.add("Bad call to device name");
//        }
//
//      }
//    }

    return listOfBTLEDevices;
  }

}
