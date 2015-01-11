package com.andyscan.gdaademo;

/**
 * Copyright 2015 Sean Janson. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **/

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;

import java.io.File;
import java.util.ArrayList;

public class MainActivity extends Activity
                                      implements ConnectionCallbacks, OnConnectionFailedListener {

  static final String DIALOG_ERROR = "dialog_error";
  static final String REQUEST_CODE = "request_code";
  static final String TMP_FILE_NAME = "tmp_file_name";

  private static final int REQ_ACCPICK = 1;
  private static final int REQ_AUTH    = 2;
  private static final int REQ_RECOVER = 3;
  private static final int REQ_SCAN    = 4;

  private static boolean mIsInAuth;
  private static TextView mDispTxt;
  private static String mTmpFlNm;

  @Override
  protected void onCreate(Bundle bundle) {   super.onCreate(bundle);
    setContentView(R.layout.activity_main);
    mDispTxt = (TextView)findViewById(R.id.tvDispText);

    UT.init(this);

    if (checkPlayServices() && checkUserAccount()) {
      GooDrive.init(this, UT.AM.getActiveEmil());
      GooDrive.connect(true);
    }
    if (bundle != null) {
      mTmpFlNm = bundle.getString(TMP_FILE_NAME);
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle bundle) {     super.onSaveInstanceState(bundle);
    bundle.putString(TMP_FILE_NAME, mTmpFlNm);  // we can be killed when in the cam activity
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_main, menu);
    return true;
  }
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.action_scan: {
        mDispTxt.setText(getString(R.string.disp_text));
        Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (it.resolveActivity(UT.acx.getPackageManager()) != null) {
          File tmpFl = null;
          try {tmpFl = File.createTempFile(UT.TMP_FILENM, null, UT.acx.getExternalCacheDir());
          } catch (java.io.IOException e) {UT.le(e); }
          if (tmpFl != null) {
            it.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(tmpFl));
            startActivityForResult(it, REQ_SCAN);
            mTmpFlNm = tmpFl.getAbsolutePath();
          }
        }
        return true;
      }

      case R.id.action_list: {
        mDispTxt.setText("running LONG test, patience please");
        new AsyncTask<Void, Void, String>() {
          @Override
          protected String doInBackground(Void... params) {
            ArrayList<GooDrive.GF>gfs = GooDrive.testTreeGDAA(UT.MYROOT);      // GDAA flavor
            //ArrayList<GooDrive.GF>gfs = GooDrive.testTreeREST(UT.MYROOT);      // RESTful flavor
            if (gfs == null)
              return null;
            String dsp = "";
            for (GooDrive.GF gf : gfs) {
              dsp += (gf.titl + "\n");
            }
            return dsp;
          }
          @Override
          protected void onPostExecute(String s) { super.onPostExecute(s);
            if (s == null)
              mDispTxt.setText("nothing found");
            else
              mDispTxt.setText(s);
          }
        }.execute();
        return true;
      }

      case R.id.action_account: {
        Account acc = UT.AM.getActiveAccnt();
        if (acc == null)
          acc = UT.AM.getPrimaryAccnt(false);
        startActivityForResult(
         AccountPicker.newChooseAccountIntent( acc,  // null value will work, no pre-selection
          null, new String[]{GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE}, true, null, null, null, null ),
          REQ_ACCPICK
        );
        return true;
      }
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    switch (request) {
      case REQ_ACCPICK: {  // return from account picker
        if (result == Activity.RESULT_OK && data != null) {        UT.lg("ACCPICK ok");
          String email = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
          if (UT.AM.setEmil(email) == UT.AM.CHANGED) {
            GooDrive.init(this, UT.AM.getActiveEmil());
            GooDrive.connect(true);
          }
        } else if (UT.AM.getActiveEmil() == null) {                UT.lg("ACCPICK cancelled");
          UT.AM.removeActiveAccnt();
          finish();
        }
        break;
      }

      case REQ_AUTH: case REQ_RECOVER: {  // from  GOOPlaySvcs recoverable failure
        mIsInAuth = false;
        if (result == Activity.RESULT_OK) {           UT.lg("AUTH RECOVER ok " + request);
          GooDrive.connect(true);
        } else if (result == RESULT_CANCELED) {       UT.lg("AUTH RECOVER cancel " + request);
          UT.AM.removeActiveAccnt();
          finish();
        }
        break;
      }

      case REQ_SCAN: {
        if (result == Activity.RESULT_OK) {                                UT.lg( "scanned");
          final String titl = UT.time2Titl(null);
          if (titl != null && mTmpFlNm != null) {
            new Thread(new Runnable() { @Override public void run() {
              File tmpFl = null;
              try {
                tmpFl = new File(mTmpFlNm);
                GooDrive.createTreeGDAA(UT.MYROOT, titl, UT.file2Bytes(tmpFl));   // GDAA flavor
                //GooDrive.createTreeREST(UT.MYROOT, titl, UT.file2Bytes(tmpFl));  // REST flavor
              } finally { if (tmpFl != null) tmpFl.delete(); }
            }}).start();
          }
        }                                                                 else UT.lg("quit");
        break;
      }
    }
    super.onActivityResult(request, result, data);
  }

  @Override
  public void onConnectionSuspended(int i) {                              UT.lg("suspended ");
  }

  @Override
  public void onConnectionFailed(ConnectionResult result) {               UT.lg("failed ");
    if (!mIsInAuth) {
      if (result.hasResolution()) {
        try {
          mIsInAuth = true;
          result.startResolutionForResult(this, REQ_AUTH);
        } catch (IntentSender.SendIntentException e) {
          finish();
        }
      } else {
        finish();
      }
    }
  }

  @Override
  public void onConnected(Bundle bundle) {                               UT.lg("connected ");
  }

  private boolean checkUserAccount() {                                UT.lg("check user acc");
    String email = UT.AM.getActiveEmil();
    Account accnt = UT.AM.getPrimaryAccnt(true);

    if (email == null) {  // no emil (after install)
      if (accnt == null) {  // multiple or no accounts available, go pick one
        accnt = UT.AM.getPrimaryAccnt(false);  // pre-select primary account if present
        Intent it = AccountPicker.newChooseAccountIntent(accnt, null,
         new String[]{GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE}, true, null, null, null, null
        );
        startActivityForResult(it, REQ_ACCPICK);
        return false;  //--------------------->>>
      } else {  // there's only one goo account registered with the device, skip the picker
        UT.AM.setEmil(accnt.name);
      }
      return true;  //------------------>>>>
    }

    // UNLIKELY BUT POSSIBLE,
    // emil's OK, but the account have been removed (through settings), re-select
    accnt = UT.AM.getActiveAccnt();
    if (accnt == null) {
      accnt = UT.AM.getPrimaryAccnt(false);
      Intent it = AccountPicker.newChooseAccountIntent(accnt, null,
               new String[]{GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE}, true, null, null, null, null
      );
      startActivityForResult(it, REQ_ACCPICK);
      return false;  //------------------>>>
    }
    return true;
  }

  private boolean checkPlayServices() {                              UT.lg("check play svcs");
    int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
    if (status != ConnectionResult.SUCCESS) {
      if (GooglePlayServicesUtil.isUserRecoverableError(status)) {
        errorDialog(status, REQ_RECOVER);
      } else {
        finish();
      }
      return false;
    }
    return true;
  }
  private void errorDialog(int errorCode, int requestCode) {
    Bundle args = new Bundle();
    args.putInt(DIALOG_ERROR, errorCode);
    args.putInt(REQUEST_CODE, requestCode);
    ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
    dialogFragment.setArguments(args);
    dialogFragment.show(getFragmentManager(), "errordialog");
  }
  public static class ErrorDialogFragment extends DialogFragment {
    public ErrorDialogFragment() { }
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      int errorCode = getArguments().getInt(DIALOG_ERROR);
      int requestCode = getArguments().getInt(DIALOG_ERROR);
      return GooglePlayServicesUtil.getErrorDialog(errorCode, getActivity(), requestCode);
    }
    @Override
    public void onDismiss(DialogInterface dialog) {
      getActivity().finish();
    }
  }

}
