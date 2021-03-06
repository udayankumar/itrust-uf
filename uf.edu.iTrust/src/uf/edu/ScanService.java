/*******************************************************************************
 * Copyright (c) 2012 Udayan Kumar.
 * All rights reserved. 
 * 
 * This file is part of iTrust application for android.
 * iTrust is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. 
 * 
 * iTrust is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with iTrust.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Udayan Kumar - initial API and implementation
 ******************************************************************************/
package uf.edu;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

/*Author: Udayan Kumar, ukumar@cise.ufl.edu*/

public class ScanService extends Service {
	private static final String TAG = "iTrust";
	private BluetoothAdapter btAdapter;
	private boolean fileWriteEnabled = false;
	private FileOutputStream fosw=null;
	private FileOutputStream fosb=null;
	private WifiManager wifiManager;
	private boolean clickedExit = false;
	private PowerManager pm = null;
	PowerManager.WakeLock wl = null;
	private SharedPreferences prefs ;
	public static final String CUSTOM_INTENT = "uf.edu.itrust.bluetoothEncounter.result";
	String msg= Long.toString(System.currentTimeMillis()/1000)+";";
	NotificationManager mNotificationManager;
	Notification notification;
	long  timeStamp=0;
	long lastTimeStamp=0;
    WifiManager.WifiLock _wifiLock = null;
    public int numOfScans;

	TreeMap<String,ScanResult> wifiApMap = new TreeMap<String,ScanResult>();
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onCreate() {
		//Toast.makeText(this, "Scan Service Created", Toast.LENGTH_LONG).show();
		Log.d(TAG, "onCreate");
		
		 // Register the BroadcastReceiver
    	IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(btRecv, filter);
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(btRecv, filter);

        //instantiate a wifi device
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if(!wifiManager.isWifiEnabled()) {
        	Log.i(TAG, "Wifi is not enabled");

        	if(!wifiManager.setWifiEnabled(true)) {
    			Toast.makeText(getApplicationContext(), "Wifi Scanning not available", Toast.LENGTH_LONG).show();
        	}
        }
        
        if(wifiManager.isWifiEnabled()) {
        	Log.i(TAG, "Wifi accessible activating intent");
        	IntentFilter wifiFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION); 
        	registerReceiver(wifiRecv, wifiFilter); 
        }
        
        //if we can write to logs.. open file stream
        if(checkExternalFileState()) {
        	fileWriteEnabled = true;
        	
        	
        	//we need to check if direction is created on SD card or not. 
        	File f = new File(Environment.getExternalStorageDirectory(),getString(R.string.DataPath));
        	if(!f.isDirectory() || !f.exists()) {
				Log.i(TAG, "Creating directory on sd card : " + getString(R.string.DataPath));
				//create one if does not exists.
				if(!f.mkdir()) {
					Log.i(TAG, "Directory creation on sd card Failed : " + getString(R.string.DataPath));
					fileWriteEnabled=false;
					Toast.makeText(this, "Cannot create Directory on SD Card. Service cannot start", Toast.LENGTH_LONG).show();
					return;
				}
        	}
        	
        	Log.i(TAG, "btscan " + Environment.getExternalStorageDirectory());
        	File filew = new File(Environment.getExternalStorageDirectory(), getString(R.string.DataPath)+"/"+"scannedDataW");
        	File fileb = new File(Environment.getExternalStorageDirectory(), getString(R.string.DataPath)+"/"+"scannedDataB");
			try {
				fosw = new FileOutputStream(filew,true);
				fosb = new FileOutputStream(fileb,true);
				//gos = new GZIPOutputStream(fos); // for enabling the gzip stream
			} catch (Exception e) {
				Log.i(TAG, "Exception thrown" + e.getMessage());
				
			}
			
        }
        else {
        	fileWriteEnabled = false;
			Toast.makeText(this, "Cannot access SD Card. Service cannot start", Toast.LENGTH_LONG).show();

        }
        //TODO: this should be moved to first time run code of iTrust. Saves the BLUE toothMAC in XML files
        BluetoothAdapter btAdapt= null; 
        btAdapt = BluetoothAdapter.getDefaultAdapter();
        SharedPreferences pres;
    	pres = this.getSharedPreferences("iTrust", 0); 
        SharedPreferences.Editor ed = pres.edit();
        ed.putString("BLUEMAC", btAdapt.getAddress());
        ed.commit();

        
        //notification 
        
        String ns = Context.NOTIFICATION_SERVICE;
        mNotificationManager = (NotificationManager) getSystemService(ns);
        
        int icon = R.drawable.icon;
        CharSequence tickerText = "iTrust scan service started";
        long when = System.currentTimeMillis();
        notification = new Notification(icon, tickerText, when);
       
        
        
        
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wl.acquire();
        
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
          _wifiLock = wifiManager.createWifiLock("0 Backup wifi lock");
          _wifiLock.acquire();
        }
        
	    numOfScans = pres.getInt("ScansPerformed",0);

	}

	  
    //checks if we can write to external storage
    public boolean checkExternalFileState() {
    	//checking the external file system state
    	String state = Environment.getExternalStorageState();

    	if (Environment.MEDIA_MOUNTED.equals(state)) {
    	    // We can read and write the media
    		return true;
    	} else  {
    	    // We can only read the media
    	    return false;
    	}
    }
    
    private final BroadcastReceiver wifiRecv = new BroadcastReceiver(){
    	public void onReceive(Context context, Intent intent){
    		String action = intent.getAction();
    		ScanResult sr = null;
    		// when we find a device
    		if ( WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
    			List<ScanResult> results = wifiManager.getScanResults();
    			if(results == null ) {
    				Log.e(TAG,"ScanService,BroadcastReceiver-wifiRecv: " + timeStamp+": Received Null results from wifi"  );
    				return;
    			}	
    			
    			Log.i(TAG, "Wifi received; size :"+ results.size());

    			if(lastTimeStamp == timeStamp) {
    				
    				//put all the macs in a arraymap
    				for(ScanResult result:results) {
    					wifiApMap.put(result.BSSID, result);
    					Log.e(TAG, timeStamp + ";"+ result.BSSID +";"+ result.SSID +";"+ result.level+";"+"\n" );
        			}
    				Log.e(TAG,"ScanService,BroadcastReceiver-wifiRecv: " + timeStamp+": received wifi scan again during same scan period"  );
    				
    			}	else {
    				// print the array map to file
    				for (Entry<String, ScanResult> entry : wifiApMap.entrySet()){
    					sr = (ScanResult)entry.getValue();
    					try {
        					fosw.write(( lastTimeStamp + ";"+ sr.BSSID +";"+ sr.SSID +";"+ sr.level+";"+"\n").getBytes() );
        					Log.d(TAG, lastTimeStamp + ";"+ sr.BSSID +";"+ sr.SSID +";"+ sr.level+";"+"\n" );
        				}catch (IOException e) {
        					// TODO Auto-generated catch block
        					e.printStackTrace();
        				}
    					
    				}
    				wifiApMap = new TreeMap<String,ScanResult>();
    				//now add the latest scan results to the treemap.
    				for(ScanResult result:results) {
    					wifiApMap.put(result.BSSID, result);
    					Log.e(TAG, timeStamp + ";"+ result.BSSID +";"+ result.SSID +";"+ result.level+";"+"\n" );
        			}
    				
    				lastTimeStamp = timeStamp;
    			}
//    			for(ScanResult result:results) {
//    				try {
//    					fosw.write(( timeStamp + ";"+ result.BSSID +";"+ result.SSID +";"+ result.level+";"+"\n").getBytes() );
//    					Log.d(TAG, timeStamp + ";"+ result.BSSID +";"+ result.SSID +";"+ result.level+";"+"\n" );
//    				}catch (IOException e) {
//    					// TODO Auto-generated catch block
//    					e.printStackTrace();
//    				}
//    			}
//    			lastTimeStamp = timeStamp;
    		}
    	}
    };
    
   
    
    
    private final BroadcastReceiver btRecv = new BroadcastReceiver(){
		public void onReceive(Context context, Intent intent){
			String action = intent.getAction();
			// when we find a device
			if(BluetoothDevice.ACTION_FOUND.equals(action)){
				// Get the BluetoothDevice object from the Intent
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				btInfo asdf = new btInfo();
				asdf.setName(device.getName());
				asdf.setMac(device.getAddress());
				if(fileWriteEnabled) {
					try {
						fosb.write(( timeStamp + ";"+ asdf.getMac() + ";"+asdf.getName() +"\n").getBytes() );
						msg +=  asdf.getMac() + ";";
						Log.d(TAG,timeStamp + ";"+ asdf.getMac() + ";"+asdf.getName() +"\n");
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}	
			} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
				//http://stackoverflow.com/questions/1043322/why-do-i-get-access-denied-to-data-folder-when-using-adb/7712173#7712173
				Log.i(TAG,"Dicovery of bluetooth device done");
				prefs = context.getSharedPreferences("iTrust",0);
				SharedPreferences.Editor ed = prefs.edit();
				ed.putString("LatestScan", msg);
				ed.commit();
				
				Intent newIntent = new Intent(ScanService.CUSTOM_INTENT);
				context.sendBroadcast(newIntent);
			}
		}
	};
	
	
	
	
	
	
	@Override
	public void onStart(Intent intent, int startid) {
		Toast.makeText(this, "Scan Service Started", Toast.LENGTH_LONG).show();
		Log.d(TAG, "onStart");
		// check if the device has a bluetooth adapter
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		if(btAdapter == null){
			Toast.makeText(getApplicationContext(), "Bluetooth not available", Toast.LENGTH_LONG).show();
		}
		if(!btAdapter.isEnabled()){
			Toast.makeText(getApplicationContext(), "Bluetooth not enabled", Toast.LENGTH_LONG).show();
			btAdapter.enable();
			//can use btAdapter.enable(); to enable bluetooth 
		}
		
		Log.i(TAG,"ScanService:onStart, Bluetooth status : " + btAdapter.getScanMode());
		if(android.os.Build.VERSION.SDK_INT	>=14 && btAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE   ) {  //if the discovery is off and check the os version.
			Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			discoverableIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
			startActivity(discoverableIntent);
		}			
		
		//notification
		Context context = getApplicationContext();
        CharSequence contentTitle = "iTrust";
        CharSequence contentText = "Scanning has started";
        Intent notificationIntent = new Intent(this, iTrust.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
        int HELLO_ID = 1;

        mNotificationManager.notify(HELLO_ID, notification);
        
		
		
		//display.clear();
		//display.add(getString(R.string.init_scan));
		new DoDiscovery().execute();
		//btAdapter.startDiscovery();
	}
	
	
	private class DoDiscovery extends AsyncTask<Object, Object, Object> {

		@Override
		protected Object doInBackground(Object... arg0) {
			while (!clickedExit) {
				timeStamp = System.currentTimeMillis()/1000;
				msg= Long.toString(System.currentTimeMillis()/1000)+";";	
				btAdapter.startDiscovery();
				while(btAdapter.isDiscovering()==true) {
					Log.e(TAG, "Bluetooth is still discovering :");
					try {
						Thread.sleep(10000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				try {
					fosw.write(( timeStamp + ";00:00:00:00:00:00;Scanned;000;"+"\n").getBytes() );
				} catch (IOException e1) {
					e1.printStackTrace();
				}	
				wifiManager.startScan();
				try {
					Thread.sleep(100000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				Log.i(TAG, "Bluetooth enabled :");
				try {
					fosb.flush();
					fosw.flush();
				} catch (IOException e) {
					Log.i(TAG,e.getMessage());
				}
				/* saving number of scans */
				SharedPreferences.Editor ed = prefs.edit();
				ed.putInt("ScansPerformed", numOfScans);
				ed.commit();
			}
			return arg0;
		}
	}

	public class btInfo{
		private String name;
		private String mac;
		
		public String getName(){
			return name; 
		}
		
		public void setName(String getname){
			this.name = getname;
		}
		
		public String getMac(){
			return mac;
		}
		
		public void setMac(String getmac){
			this.mac = getmac;
		}
	}

	@Override
	public void onDestroy() {
		Toast.makeText(this, "Scan Service Stopped", Toast.LENGTH_LONG).show();
		
		//notification
		Context context = getApplicationContext();
		CharSequence contentTitle = "iTrust";
		CharSequence contentText = "Scanning has stopped";
		Intent notificationIntent = new Intent(this, iTrust.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
		int HELLO_ID = 1;
		mNotificationManager.notify(HELLO_ID, notification);

		
		Log.d(TAG, "onDestroy");
		try {
			fosw.close();
			fosb.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		this.clickedExit=true;
		try {
			this.unregisterReceiver(btRecv);
			this.unregisterReceiver(wifiRecv);
		} catch (Exception e) {
			Log.e(TAG, "ScanService:destroy, Exception: "+ e.getMessage());
		}
		wl.release();
		_wifiLock.release();
	}
	
}
