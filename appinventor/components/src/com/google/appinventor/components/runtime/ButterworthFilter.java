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
import com.google.appinventor.components.runtime.EventDispatcher;


import java.util.List;
import java.util.ArrayList;
import java.lang.Math;

/**
 * Implementing a butterworth file
 *
 * @author  David Garrett (not the violionist)
 */
@DesignerComponent(version = YaVersion.WICEDSENSE_COMPONENT_VERSION,
    category = ComponentCategory.WICED,
    description = "Building a Butterworth filter block",
    nonVisible = true,
    iconName = "images/wicedSenseIcon.png")
@SimpleObject
public final class ButterworthFilter extends AndroidNonvisibleComponent 
implements Component {

  private static final String LOG_TAG = "ButterworthFilter";
  private final Activity activity;

  // holds error message
  private boolean mLogEnabled = true;
  private String mLogMessage = "";

  // Sets mode
  private int BYPASS_MODE = 0;
  private int LPF_MODE = 1;
  private int HPF_MODE = 2;
  private int BPF_MODE = 3;

  // Global input variables
  private boolean mLowpassEnabled = true;
  private boolean mHighpassEnabled = false;
  private double mFlc = 0.25;
  private double mFhc = 0.35;
  private double mFs = 1.00;
  private int mOrder = 4;

  // sets up filter mode
  private int mFilterMode = BYPASS_MODE;
  private int mNumStages = 1;
  private int mNumCoef = 2;
  private int mFilterDelay = 0;

  private final int numFilterResponseTaps = 16;
  private int[] mFilterResponse;

  // Holds filter
  private double[] gain;
  private double[][] coef;
  private double[][] w;

  /**
   * Creates a new Butterworth Filter Component
   *
   * @param container the enclosing component
   */
  public ButterworthFilter (ComponentContainer container) {
    super(container.$form());
    activity = container.$context();

    // names the function
    String functionName = "ButterworthFilter";

    mFilterResponse = new int[16];

    // set the filter response 
    calcFilterResponse();

    // reset the filter
    resetFilter();

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

  /** ----------------------------------------------------------------------
   *  Filter Section
   *  ----------------------------------------------------------------------
   */
  public void calcFilterResponse() { 
    LogMessage("Calculating the new filter coefficients", "i");

    double r, s; 
    double a, a2, b, b2;
    double mF1, mF2;

    // Setup default filter mode
    mFilterMode = BYPASS_MODE;
    mF1 = 0.0;
    mF2 = 0.0;

    // Setup Band-pass filter
    if (mLowpassEnabled && mHighpassEnabled) {
      mFilterMode = BPF_MODE;
      mF1 = mFhc;
      mF2 = mFlc;
      if ((mF1 == mF2) || (mF1 > mF2) || (mF1 == 0.0) || (mF2 == 0.0) 
           || (mF1 > (mFs/2.0)) || (mF2 > (mFs/2.0))) { 
        mFilterMode = BYPASS_MODE;
      }
    // Setup low-pass filter
    } else if (mLowpassEnabled) { 
      mF1 = mFlc;
      if ((mF1 == 0.0) || (mF1 > (mFs/2.0))) { 
        mFilterMode = BYPASS_MODE;
      } else { 
        mFilterMode = LPF_MODE;
      }
    // Setup high-pass filter
    } else if (mHighpassEnabled) { 
      mF1 = mFhc;
      if ((mF1 == 0.0) || (mF1 > (mFs/2.0))) { 
        mFilterMode = BYPASS_MODE;
      } else { 
        mFilterMode = HPF_MODE;
      }
    }

    // initialize the filter response - calculated later
    for (int loop1 = 0; loop1 < numFilterResponseTaps; loop1++) { 
      mFilterResponse[loop1] = 0;
    }
    
    // Configure the array sizes (and round up to number of stages)
    if (mFilterMode == BYPASS_MODE) { 
      mFilterDelay = 0;
      return;
    } else if (mFilterMode == BPF_MODE) { 
      mNumStages = (mOrder + 3) / 4;
      mNumCoef = 4;
    } else { 
      mNumStages = (mOrder + 1) / 2;
      mNumCoef = 2;
    }

    // allocate new arrays
    double[]   newGain = new double[mNumStages];
    double[][] newCoef = new double[mNumStages][mNumCoef];
    double[][] newState = new double[mNumStages][mNumCoef];

    // ---------------------------------------------------------
    //  Calculate the Band-pass coefficients
    // ---------------------------------------------------------
    if (mFilterMode == BPF_MODE) {
      a = Math.cos(Math.PI*(mF1+mF2)/mFs)/Math.cos(Math.PI*(mF2-mF1)/mFs);
      a2 = a*a;
      b = Math.tan(Math.PI*(mF2-mF1)/mFs);
      b2 = b*b;
      s = mFs;

      for (int loop1 = 0; loop1 < mNumStages; loop1++) { 

        r = Math.sin(Math.PI*(2.0*loop1+1.0)/(4.0*mNumStages));
        s = a2 + 2.0*a*r + 1.0;

        newGain[loop1] = b2/mFs;
        newCoef[loop1][0] = 4.0*a*(1.0+b*r)/s;
        newCoef[loop1][1] = 2.0*(b2-2.0*a2-1)/s;
        newCoef[loop1][2] = 4.0*a*(1.0-b*r)/s;
        newCoef[loop1][3] = -(b2 - 2.0*b*r+1.0)/s;

      }
    // --------------------------------------------------------------
    //  Calculate the Low and high-pass coefficients (slight tweaks)
    // --------------------------------------------------------------
    } else { 
      a = Math.tan(Math.PI*mF1/mFs);
      a2 = a*a;
      s = mFs;

      // loop over stages
      for (int loop1 = 0; loop1 < mNumStages; loop1++) { 

        r = Math.sin(Math.PI*(2.0*loop1+1.0)/(4.0*mNumStages));
        s = a2 + 2.0*a*r + 1.0;

        if (mFilterMode == LPF_MODE) { 
           newGain[loop1] = a2/s;
        } else {
           newGain[loop1] = 1.0/s;
        }
        newCoef[loop1][0] = 2.0*(1-a2)/s;
        newCoef[loop1][1] = -(a2 - 2.0*a*r+1.0)/s;

      }

    }
    
    // update arrays
    coef = newCoef;
    gain = newGain;
    w = newState;

    // Runs the filter analysis
    analyzeFilter();

  }

  // Run the filter through FFT analysis 
  public void analyzeFilter() { 
    double [] filterOut = new double[256];

    // push through impulse resonse into filter
    resetFilter();
    for (int loop1 = 0; loop1 < 256; loop1++) {  
      if (loop1 == 0){ 
        filterOut[loop1] = applyFilter(1.0);
      } else { 
        filterOut[loop1] = applyFilter(0.0);
      }
    }
    resetFilter();

    // Find maximum energy out
    double maxOut = 0.0;
    mFilterDelay = 0;
    for (int loop1 = 0; loop1 < 256; loop1++) {  
      if (Math.abs(filterOut[loop1]) > maxOut) { 
        maxOut = Math.abs(filterOut[loop1]);
        mFilterDelay = loop1;
      }
    }
  
    // Run the FFT, and sub-sample response
   // double[] x = new double[2];
   // double[] exp = new double[2];
   // int span = 128;
   // int spacing = 2;
   // for (int loop1 = 0; loop1 < 8; loop1++) { 
   //   for (int loop2 = 0; loop2 < 128; loop2++) { 
   //     x[0] = filterOut
   //   }
   // }

  }

  // reset the filter
  public void resetFilter() { 
    LogMessage("Reseting the filter", "i");
    // clear the internal state
    for (int loop1 = 0; loop1 < mOrder; loop1++) { 
      for (int loop2 = 0; loop2 < mNumCoef; loop1++) { 
        w[loop1][loop2] = 0.0; 
      }
    }
  }

  public double applyFilter(double inValue) { 
    LogMessage("Applying filter", "i");
    double outValue;
    double x;
    double w0;

    // do nothing for bypass mode
    if (mFilterMode == BYPASS_MODE) { 
      outValue = inValue;
    // Run the Filter
    } else { 
    
      // copy input to x
      x = inValue;

      // loop over the number of stages
      for (int loop1 = 0; loop1 < mNumStages; loop1++) { 

        // run the filter
        w0 = 0.0;
        for (int loop2 = 0; loop2 < mNumCoef; loop2++) { 
           w0 += coef[loop1][loop2] * w[loop1][loop2];
        }
  
        // depending on filter, run the output calculation
        if (mFilterMode == BPF_MODE) {
          x = gain[loop1]*(w0 - 2*w[loop1][1] + w[loop1][3]);
        } else if (mFilterMode == LPF_MODE) {
          x = gain[loop1]*(w0 + 2*w[loop1][0] + w[loop1][1]);
        } else { 
          x = gain[loop1]*(w0 - 2*w[loop1][0] + w[loop1][1]);
        } 
  
        // update stage
        for (int loop2 = mNumCoef-1; loop2 > 0; loop2--) { 
          w[loop1][loop2] = w[loop1][loop2-1];
        }
        w[loop1][0] = w0;

      }

      // set the output value
      outValue = x;

    }

    return outValue;

  }

  /* ----------------------------------------------------------------------
   * ----------------------------------------------------------------------
   * GUI Interface Code Section
   * ----------------------------------------------------------------------
   * ----------------------------------------------------------------------
   */

  /**
   * Allows the user to reset the filter
   */
  @SimpleFunction(description = "Resets the filter")
  public void Reset() { 
    resetFilter();
  }

  /**
   * Pushes a sample into the filter and gets current output
   */
  @SimpleFunction(description = "Resets the filter")
  public float Filter(float inputSample) { 
    return (float)applyFilter((double)inputSample);
  }

  /**  ----------------------------------------------------------------------
   *   Properties of the Device
   *   ----------------------------------------------------------------------
   */

  /** Gets Filter Order */
  @SimpleProperty(description = "Returns the Filter order.", 
                  category = PropertyCategory.BEHAVIOR,
                  userVisible = true)
  public int Order() {
    return mOrder;
  }

  /** Sets high pass cutoff Frequency */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_NON_NEGATIVE_FLOAT,
                    defaultValue = 0.35 + "")
  @SimpleProperty(description = "Sets the High pass cutoff frequency in Hz.", 
                  category = PropertyCategory.BEHAVIOR,
                  userVisible = true)
  public void HighpassCutoff(float mFhcVal) {
    mFhc = mFhcVal;
  }

  /** Sets Low pass cutoff Frequency */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_NON_NEGATIVE_FLOAT,
                    defaultValue = 0.25 + "")
  @SimpleProperty(description = "Sets the Low pass cutoff frequency in Hz.", 
                  category = PropertyCategory.BEHAVIOR,
                  userVisible = true)
  public void LowpassCutoff(float mFlcVal) {
    if (mFlcVal != mFlc) { 
      mFlc = mFlcVal;
      calcFilterResponse();
    }
  }

  /** Sets Sampling Frequency */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_NON_NEGATIVE_FLOAT,
                    defaultValue = 1.0 + "")
  @SimpleProperty(description = "Sets the Sampling frequency in HZ.", 
                  category = PropertyCategory.BEHAVIOR,
                  userVisible = true)
  public void SamplingFrequency(float mFsVal) {
    if (mFsVal != mFs) { 
      mFs = mFsVal;
      calcFilterResponse();
    }
  }

  /** Sets Filter Order */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_NON_NEGATIVE_INTEGER,
                    defaultValue = 4 + "")
  @SimpleProperty(description = "Sets the filter order.", 
                  category = PropertyCategory.BEHAVIOR,
                  userVisible = true)
  public void Order(int mOrderVal) {
    if (mOrderVal != mOrder) { 
      mOrder = mOrderVal;
      calcFilterResponse();
    }
  }

  /**
   * Enables low pass filtering 
   *
   */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
                    defaultValue = "True")
  @SimpleProperty(description = "Returns if low pass filtering is enabled", 
                  category = PropertyCategory.BEHAVIOR,
                  userVisible = true)
  public void Lowpass(boolean enableFlag) {
    if (mLowpassEnabled != enableFlag) { 
      mLowpassEnabled = enableFlag;
      calcFilterResponse();
    }
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
                    defaultValue = "False")
  @SimpleProperty(description = "Sets the high pass filtering mode", 
                  category = PropertyCategory.BEHAVIOR,
                  userVisible = true)
  public void Highpass(boolean enableFlag) {
    if (mHighpassEnabled != enableFlag) { 
      mHighpassEnabled = enableFlag;
      calcFilterResponse();
    }
  }

  // Reports the filter delay (approximate)
  @SimpleProperty(description = "Returns to approximate filter delay in sample", 
                  category = PropertyCategory.BEHAVIOR,
                  userVisible = true)
  public int FilterDelay() { 
    return mFilterDelay;
  }

  // Reports the filter delay (approximate)
  @SimpleProperty(description = "Returns the filter response [0,pi) in dB", 
                  category = PropertyCategory.BEHAVIOR,
                  userVisible = true)
  public List<Integer> FilterResponse() { 
    List<Integer> H = new ArrayList<Integer>();

    if (mFilterMode == BYPASS_MODE) { 
      H.add(0);  
    } else { 
      for (int loop1 = 0; loop1 < numFilterResponseTaps; loop1++) { 
        H.add(mFilterResponse[loop1]);  
      }
    }
    return H;
  }

}
