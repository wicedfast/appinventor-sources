// -*- mode: java; c-basic-offset: 2; -*-
//
// Copyright 2014 - David Garrett - Broadcom Corporation
// http://www.apache.org/licenses/LICENSE-2.0
//
//
// -- The Butterworth filter math was derived from this site:
//     www.extstrom.com/journal/sigproc
//    The filter math released under GNU GPL version 2 or later
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
import java.lang.Integer;


/**
 * Implementing a butterworth file
 *
 * @author  David Garrett (not the violionist)
 */
@DesignerComponent(version = YaVersion.BUTTERWORTH_FILTER_COMPONENT_VERSION,
    category = ComponentCategory.SENSORS,
    description = "Building a Butterworth filter block",
    nonVisible = true,
    iconName = "images/butterworthFilterIcon.png")
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
  private double mFlc = 0.30;
  private double mFhc = 0.20;
  private double mFs = 1.00;
  private int mOrder = 4;

  // sets up filter mode
  private int mFilterMode = BYPASS_MODE;
  private int mNumStages = 1;
  private int mNumCoef = 2;

  // Characterize the filter
  private int mFilterDelay = -1;
  private double[] mFilterResponse;

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
    mFilterDelay = -1;


    // Setup Band-pass filter
    if (mLowpassEnabled && mHighpassEnabled) {
      mFilterMode = BPF_MODE;
      mF1 = mFhc;
      mF2 = mFlc;
      if ((mF1 == mF2) || (mF1 > mF2) || (mF1 == 0.0) || (mF2 == 0.0)
           || (mF1 > (mFs/2.0)) || (mF2 > (mFs/2.0))) {
        mFilterMode = BYPASS_MODE;
        LogMessage("Bandpass filters settings are in error, 0 < Fhc < Flc < Fs/2", "e");
        LogMessage(" Fhc = " + mF1 + ", Flc = " + mF2 + ", Fs/2 = " + (mFs/2.0), "e");
      }
    // Setup low-pass filter
    } else if (mLowpassEnabled) {
      mF1 = mFlc;
      if ((mF1 == 0.0) || (mF1 > (mFs/2.0))) {
        mFilterMode = BYPASS_MODE;
        LogMessage("Lowpass filters settings are in error, 0 < Fhc < Fs/2", "e");
        LogMessage(" Flc = " + mF1 + ", Fs/2 = " + (mFs/2.0), "e");
      } else {
        mFilterMode = LPF_MODE;
      }
    // Setup high-pass filter
    } else if (mHighpassEnabled) {
      mF1 = mFhc;
      if ((mF1 == 0.0) || (mF1 > (mFs/2.0))) {
        LogMessage("Highpass filters settings are in error, 0 < Fhc < Fs/2", "e");
        LogMessage(" Fhc = " + mF1 + ", Fs/2 = " + (mFs/2.0), "e");
        mFilterMode = BYPASS_MODE;
      } else {
        mFilterMode = HPF_MODE;
      }
    }

    // Configure the array sizes (and round up to number of stages)
    if (mFilterMode == BYPASS_MODE) {
      LogMessage("Setting up bypass mode", "i");
      return;
    } else if (mFilterMode == BPF_MODE) {
      mNumStages = (mOrder + 3) / 4;
      mNumCoef = 4;
      LogMessage("Setting up " + mNumStages*4 + "th order Bandpass Filter with Fs = " + mFs, "i");
      LogMessage("Passband (" + mF1 + "," + mF2 + ")", "i");
    } else {
      mNumStages = (mOrder + 1) / 2;
      mNumCoef = 2;
      if (mFilterMode == LPF_MODE) {
        LogMessage("Setting up " + mNumStages*2 +"th order Lowpass Filter with Fs = " + mFs, "i");
      } else {
        LogMessage("Setting up " + mNumStages*2 +"th order Highpass Filter with Fs = " + mFs, "i");
      }
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
        s = b2 + 2.0*b*r + 1.0;

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


    // report filter
    for (int loop1 = 0; loop1 < mNumStages; loop1++) {
      LogMessage("gain(" + loop1 + ") = " + gain[loop1], "i");
    }
    for (int loop1 = 0; loop1 < mNumStages; loop1++) {
      for (int loop2 = 0; loop2 < mNumCoef; loop2++) {
        LogMessage("coef(" + loop1 + "," + loop2 + ") = " + coef[loop1][loop2], "i");
      }
    }
  }

  // -----------------------------------------------
  // Run the filter through FFT analysis
  // -----------------------------------------------
  public void analyzeFilter(int Nfft, boolean runFFT) {


    // Special case, with Bypass mode
    if (mFilterMode == BYPASS_MODE) {
      mFilterDelay = 0;
      if (runFFT) {
        mFilterResponse = new double[Nfft/2];
        for (int loop1 = 0; loop1 < Nfft/2; loop1++) {
          mFilterResponse[loop1] = 0.0;
        }
      }
      return;
    }

    // ------------------------------------------------
    //  Analyze the regular filter
    // ------------------------------------------------
    double [][] wSave = new double[mNumStages][mNumCoef];
    double [] filterOut = new double[Nfft];

    // save filter state
    for (int loop1 = 0; loop1 < mNumStages; loop1++) {
      for (int loop2 = 0; loop2 < mNumCoef; loop2++) {
        wSave[loop1][loop2] = w[loop1][loop2];
      }
    }
    resetFilter();

    // push through impulse resonse into filter
    for (int loop1 = 0; loop1 < Nfft; loop1++) {
      if (loop1 == 0){
        filterOut[loop1] = applyFilter(1.0);
      } else {
        filterOut[loop1] = applyFilter(0.0);
      }
    }

    // return filter state to original
    for (int loop1 = 0; loop1 < mNumStages; loop1++) {
      for (int loop2 = 0; loop2 < mNumCoef; loop2++) {
        w[loop1][loop2] = wSave[loop1][loop2];
      }
    }
    LogMessage("Finished filtering impulse response", "i");

    // Find maximum energy out
    double maxOut = 0.0;
    mFilterDelay = 0;
    for (int loop1 = 0; loop1 < Nfft; loop1++) {
      if (Math.abs(filterOut[loop1]) > maxOut) {
        maxOut = Math.abs(filterOut[loop1]);
        mFilterDelay = loop1;
      }
    }
    LogMessage("Filter delay is " + mFilterDelay + " samples", "i");

    // ---------------------------------------
    //  This runs impulse respone through FFT
    // ---------------------------------------
    if (runFFT) {

      // allocate space
      mFilterResponse = new double[Nfft/2];

      // get the Nfft exp
      int NfftExp = 0;
      while (( 1 << NfftExp) < Nfft) {
        NfftExp++;
      }
      LogMessage("Running 2^" + NfftExp + "-pt FFT analysis", "i");

      // Run the FFT, and sub-sample response
      int index;
      double [] Hreal = new double[Nfft];
      double [] Himag = new double[Nfft];
      // first get to Bit-reverse addressing input data
      for (int loop1 = 0; loop1 < Nfft; loop1++) {
        index = (Integer.reverse(loop1) >> (32 - NfftExp)) & (Nfft-1);
        //LogMessage("Input index = " + loop1 + ", BRO index = " + index,"i");
        Hreal[loop1] = filterOut[index];
        Himag[loop1] = 0.0;
      }
      // setup spacing
      int spacing = 1;
      int currentN;
      double []xReal = new double[2];
      double []xImag = new double[2];
      double twiddleAngle;

      // Run Radix2 FFT loops - could be improve (radix4, etc, real-only version (packed complex at Nfft/2)
      for (int loop1 = 0; loop1 < NfftExp; loop1++) {
        currentN = spacing*2;
        for (int loop2 = 0; loop2 < (Nfft/currentN); loop2++) {
          for (int loop3 = 0; loop3 < spacing; loop3++) {
            index = loop2*currentN + loop3;
            twiddleAngle = 2.0*Math.PI*(double)loop3 / (double)currentN;
            //LogMessage("Running FFT (" + loop1 + "," + loop2 + "," + loop3 + ") on [" + index + "," + (index+spacing) + "]", "i");
            //LogMessage("Twiddle Factor = " + Math.cos(twiddleAngle) + " +j " + Math.sin(twiddleAngle), "i");

            xReal[0] = Hreal[index];
            xImag[0] = Himag[index];
            // apply twiddle factors
            xReal[1] = Hreal[index+spacing] * Math.cos(twiddleAngle)
                     - Himag[index+spacing] * Math.sin(twiddleAngle);
            xImag[1] = Hreal[index+spacing] * Math.sin(twiddleAngle)
                     + Himag[index+spacing] * Math.cos(twiddleAngle);

            // DIT matrix
            Hreal[index] = xReal[0] + xReal[1];
            Himag[index] = xImag[0] + xImag[1];
            Hreal[index+spacing] = xReal[0] - xReal[1];
            Himag[index+spacing] = xImag[0] - xImag[1];
          }
        }
        spacing *= 2;
      }

      // Convert filter response to Filter magnitude in dB
      double maxValue, tmp1;
      maxValue = -250.0;
      LogMessage("Calculating filter response from [0,pi)", "i");
      for (int loop1 = 0; loop1 < Nfft/2; loop1++) {
        tmp1 = Math.pow(Hreal[loop1],2) + Math.pow(Himag[loop1],2);
        if (tmp1 == 0.0) {
          tmp1 = -250;
        } else {
          tmp1 = 10.0*Math.log10(tmp1);
        }
        if (tmp1 > maxValue) {
          maxValue = tmp1;
        }
        mFilterResponse[loop1] = tmp1;
      }
      for (int loop1 = 0; loop1 < Nfft/2; loop1++) {
        mFilterResponse[loop1] = mFilterResponse[loop1] - maxValue;
        double ratio = (double)loop1 / (double)(Nfft/2);
        LogMessage("  H(" + ratio + ") = " + mFilterResponse[loop1] + " dB", "i");
      }
    }

  }

  // -------------------------------------------------
  // reset the filter
  // -------------------------------------------------
  public void resetFilter() {
    LogMessage("Reseting the filter", "i");
    // clear the internal state
    for (int loop1 = 0; loop1 < mNumStages; loop1++) {
      for (int loop2 = 0; loop2 < mNumCoef; loop2++) {
        w[loop1][loop2] = 0.0;
      }
    }
  }

  public double applyFilter(double inValue) {
//    LogMessage("Applying filter on " + inValue, "i");
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
        w0 = x;
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

 //   LogMessage("Filtered value = " + outValue, "i");
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
  @SimpleFunction(description = "Runs samples through the filter")
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
                    defaultValue = 0.20 + "")
  @SimpleProperty(description = "Sets the High pass cutoff frequency in Hz.",
                  category = PropertyCategory.BEHAVIOR,
                  userVisible = true)
  public void HighpassFreq(float mFhcVal) {
    if (mFhcVal != mFhc) {
      mFhc = mFhcVal;
      calcFilterResponse();
    }
  }

  /** Sets Low pass cutoff Frequency */
  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_NON_NEGATIVE_FLOAT,
                    defaultValue = 0.30 + "")
  @SimpleProperty(description = "Sets the Low pass cutoff frequency in Hz.",
                  category = PropertyCategory.BEHAVIOR,
                  userVisible = true)
  public void LowpassFreq(float mFlcVal) {
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
  public void SamplingFreq(float mFsVal) {
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

    // if not calculated, run the filter response
    int Nfft = 256;
    if (mFilterDelay == -1) {
      analyzeFilter(Nfft, false);
    }
    return mFilterDelay;

  }

  // Reports the filter response in dB (approximate)
  @SimpleFunction(description = "Returns the filter response [0,pi) in dB")
  public List<Number> FilterResponse(int numValues) {
    List<Number> H = new ArrayList<Number>();

    // convert to pow-of-two value
    int Nfft = 1;
    while (Nfft < numValues) {
      Nfft = Nfft*2;
    }
    Nfft = Nfft * 2;

    // Analyze the filter
    analyzeFilter(Nfft, true);

    // return value
    for (int loop1 = 0; loop1 < Nfft/2; loop1++) {
      if (mFilterMode == BYPASS_MODE) {
        H.add(0.0);
      } else {
        H.add(mFilterResponse[loop1]);
      }
    }
    return H;
  }

}
