package arpspoof;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.beardedhen.androidbootstrap.BootstrapButton;
import com.beardedhen.androidbootstrap.BootstrapEditText;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;

import gr.rambou.arpandroid.R;

public class Arpspoof extends AppCompatActivity {
    /**
     * Called when the activity is first created.
     */
    public static final int BUFFER_SIZE = 4096;
    public static final String TCPDUMP = "arpspoof_tcpdump";
    private static final int WAIT = 1000;
    private static final int UNROOTED_ALERT = 0;
    private static final int LICENSE_ALERT = 1;
    private static final int INSTALLED_ALERT = 2;
    private static final String TAG = "Arpspoof.main";
    private static final String FILENAME = "arpspoof";
    private static final String BINPATH = "/data/local/bin/";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.arpspoof);

		/*make sure we're using the latest binary versions*/
        SharedPreferences settings = getPreferences(MODE_PRIVATE);
        String binVersionSetting = "binVersion";
        int localBinVersion = settings.getInt(binVersionSetting, 0);
        int currentVersion;
        try {
            currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (NameNotFoundException e) {
            Log.w(TAG, "couldn't find this app's package name(really weird)", e);
            currentVersion = 0;//if something goes wrong assume it's an old version
        }

        File localBin = getFileStreamPath(FILENAME);
        File localTcpdump = getFileStreamPath(TCPDUMP);
        if (localBin.exists() == false || localBinVersion < currentVersion)
            extractBinary(R.raw.arpspoof, FILENAME);
        if (localTcpdump.exists() == false || localBinVersion < currentVersion)
            extractBinary(R.raw.arpspoof_tcpdump, TCPDUMP);
        if (localBinVersion < currentVersion) {
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt(binVersionSetting, currentVersion);
            if (editor.commit() != true)
                Log.w(TAG, "failed to commit version setting");
        }

		/*Advanced settings that are only visible when advanced is checked*/
        final TextView tcpdumpText = (TextView) findViewById(R.id.tcpdumpText);
        final BootstrapEditText tcpdumpEdit = (BootstrapEditText) findViewById(R.id.tcpdumpFilter);
        final TextView targetText = (TextView) findViewById(R.id.targetText);
        final BootstrapEditText target = (BootstrapEditText) findViewById(R.id.target);

        final CheckBox advanced = (CheckBox) findViewById(R.id.advancedCB);
        advanced.setChecked(false);
        advanced.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton bv, boolean checked) {
                if (checked) {
                    tcpdumpText.setVisibility(View.VISIBLE);
                    tcpdumpEdit.setVisibility(View.VISIBLE);
                    targetText.setVisibility(View.VISIBLE);
                    target.setVisibility(View.VISIBLE);
                } else {
                    tcpdumpText.setVisibility(View.GONE);
                    tcpdumpEdit.setVisibility(View.GONE);
                    targetText.setVisibility(View.GONE);
                    target.setVisibility(View.GONE);
                }
            }
        });

		/*Implements the Begin Spoofing button*/
        final BootstrapButton startButton = (BootstrapButton) findViewById(R.id.start);
        startButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                WifiManager wManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                WifiInfo wInfo = wManager.getConnectionInfo();

                //Check to see if we're connected to wifi
                int localhost = wInfo.getIpAddress();
                if (localhost != 0) {
                    final BootstrapEditText gateway = (BootstrapEditText) findViewById(R.id.gateway);
                    String gatewayIP = gateway.getText().toString();
                    String localhostIP = Formatter.formatIpAddress(localhost);
                    //If nothing was entered for the ip address use the gateway
                    if (gatewayIP.trim().equals(""))
                        gatewayIP = Formatter.formatIpAddress(wManager.getDhcpInfo().gateway);

                    //determining wifi network interface
                    InetAddress localInet;
                    String interfaceName = null;
                    try {
                        localInet = InetAddress.getByName(localhostIP);
                        NetworkInterface wifiInterface = NetworkInterface.getByInetAddress(localInet);
                        interfaceName = wifiInterface.getDisplayName();
                    } catch (UnknownHostException e) {
                        Log.e(TAG, "error getting localhost's InetAddress", e);
                    } catch (SocketException e) {
                        Log.e(TAG, "error getting wifi network interface", e);
                    }
                    Intent intent = new Intent(v.getContext(), SpoofingActivity.class);
                    //Add data necessary for running the program
                    Bundle mBundle = new Bundle();
                    mBundle.putString("gateway", gatewayIP);
                    mBundle.putString("localBin", getFileStreamPath(FILENAME).toString());
                    mBundle.putString("interface", interfaceName);
                    if (advanced.isChecked()) {
                        mBundle.putString("tcpdumpFilter", tcpdumpEdit.getText().toString());
                        String targetString = target.getText().toString();
                        if (!targetString.trim().equals(""))
                            mBundle.putString("target", targetString);
                    } else
                        mBundle.putString("tcpdumpFilter", getResources().getString(R.string.tcpdumpFilter));
                    intent.putExtras(mBundle);
                    if (RootAccess.isGranted())
                        startActivity(intent);
                    else
                        showDialog(UNROOTED_ALERT);
                } else {
                    CharSequence text = "Must be connected to wireless network.";
                    Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
                }
            }
        });

    }

    @Override
    protected Dialog onCreateDialog(int id) {
        AlertDialog dialog;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        switch (id) {
            case UNROOTED_ALERT:
                builder.setMessage(R.string.rootError);
                builder.setCancelable(false);
                builder.setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                });
                dialog = builder.create();
                break;
            case INSTALLED_ALERT:
                builder.setMessage(R.string.successfulInstall);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
                dialog = builder.create();
                break;
            default:
                dialog = null;
        }
        return dialog;
    }

    private void extractBinary(int id, String fileName) {
        /*extracts the binary from the apk and makes it executable.
		 * If any step fails and the function continues to run everything should be cleaned up*/
        if (RootAccess.isGranted()) {
            final InputStream arpBin = getResources().openRawResource(id);
            FileOutputStream out = null;
            boolean success = true;
            final byte[] buff = new byte[BUFFER_SIZE];
            try {
                out = openFileOutput(fileName, Context.MODE_PRIVATE);
                while (arpBin.read(buff) > 0)
                    out.write(buff);
            } catch (FileNotFoundException e) {
                Log.e(TAG, fileName + "wasn't found", e);
                success = false;
            } catch (IOException e) {
                Log.e(TAG, "couldn't extract executable", e);
                success = false;
            } finally {
                try {
                    out.close();
                } catch (IOException e) {
                    // swallow error
                    Log.e(TAG, "Fucking error?", e);
                }
            }
            try {
                ExecuteCommand ec = new ExecuteCommand("chmod 770 " + getFileStreamPath(fileName).toString());
                ec.start();
                ec.join();
            } catch (IOException e) {
                Log.e(TAG, "error running chmod on local file", e);
                success = false;
            } catch (InterruptedException e) {
                Log.i(TAG, "thread running chmod was interrupted");
                success = false;
            } finally {
                if (!success)
                    getFileStreamPath(fileName).delete();
            }
        } else
            showDialog(UNROOTED_ALERT);

    }

}