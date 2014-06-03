package com.jeremyutting.localactions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONException;
import org.json.JSONObject;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.estimote.sdk.Utils;
import com.estimote.sdk.utils.L;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v4.app.NavUtils;

public class ScanActivity extends Activity {
	
	private String bridgeIP = "192.168.1.2";
	private String hueAPIKey = "comp550app";
	private Button stateButton;
	private int nearestBeacon = 0;
	private boolean lightOn = true;
	private int bulbNumber = 2;
	private static final String TAG = ScanActivity.class.getSimpleName();
	private final static int REQUEST_ENABLE_BT = 1;
	
	// This region means that we're only scanning for Estimote beacons, and not ALL BlueTooth devices.
	private static final Region ALL_ESTIMOTE_BEACONS = new Region("rid", null, null, null);
	
	
	private BeaconManager beacon_manager;
	private LeDeviceListAdapter adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Link this activity to the activity_scan.xml layout
		setContentView(R.layout.activity_scan);
		
		L.enableDebugLogging(true);
		
		stateButton = (Button) findViewById(R.id.on_off_button);
		
		// Create our custom ListView adapter, and link it with our view
		adapter = new LeDeviceListAdapter();
		ListView list = (ListView) findViewById(R.id.device_list);
		list.setAdapter(adapter);
		
		// Create and initialize Estimote BeaconManager, and set the scan period to 200ms 
		beacon_manager = new BeaconManager(this);
		beacon_manager.setForegroundScanPeriod(200, 0);
		// Ranging is the act of scanning for beacons
		beacon_manager.setRangingListener(new BeaconManager.RangingListener() {
			
			@Override
			public void onBeaconsDiscovered(Region region, final List<Beacon> beacons) {
				runOnUiThread(new Runnable() {

					// Update the title bar when a beacon is found.
					@Override
					public void run() {
						//getActionBar().setSubtitle("Found " + beacons.size() + " beacons");
						adapter.replaceWith(beacons);
					}
				});
			}
		});
		
		ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
		if (networkInfo != null && networkInfo.isConnected()) {
			Timer timer = new Timer();
			timer.scheduleAtFixedRate(new TimerTask() {

				@Override
				public void run() {
					new FetchAPITask().execute();
				}
				
			}, 0, 2000);
		} else {
			// No network connection - do something.
		}
		
		// Show the Up button in the action bar.
		setupActionBar();
	}

	/**
	 * Set up the {@link android.app.ActionBar}.
	 */
	private void setupActionBar() {

		getActionBar().setDisplayHomeAsUpEnabled(true);

	}
	
	public void lightOnOff(View view) {
		new SendStateAPITask().execute();
	}
	
	public void changeLightColor(View view) {
		if (nearestBeacon == 0) {
			Log.d(TAG, "beacon not set, returning");
			return;
		} else {
			new SendColorStateAPITask().execute();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.scan, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			// This ID represents the Home or Up button. In the case of this
			// activity, the Up button is shown. Use NavUtils to allow users
			// to navigate up one level in the application structure. For
			// more details, see the Navigation pattern on Android Design:
			//
			// http://developer.android.com/design/patterns/navigation.html#up-vs-back
			//
			NavUtils.navigateUpFromSameTask(this);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	protected void onDestroy() {
		// When the app is destroyed, disconnect the beacon manager
		beacon_manager.disconnect();
		
		super.onDestroy();
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		
		// If Bluetooth is not enabled, let user enable it.
	    if (!beacon_manager.isBluetoothEnabled()) {
	      Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
	      startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
	    } else {
	    	// If BlueTooth is enabled, we're good to start ranging.
	    	connectToService();
	    }
	}
	
	@Override
	protected void onStop() {
		// Attempt to stop reforming the Ranging action
		try {
			beacon_manager.stopRanging(ALL_ESTIMOTE_BEACONS);
		} catch (RemoteException e) {
			Log.d(TAG, "Error while stopping ranging", e);
		}
		super.onStop();
	}
	
	private void connectToService() {
		getActionBar().setSubtitle("Scanning...");
		// Wipe the beacon list
		adapter.replaceWith(Collections.<Beacon>emptyList());
		beacon_manager.connect(new BeaconManager.ServiceReadyCallback() {

			// When the scanning service is available as defined by our interval, perform ranging.
			@Override
			public void onServiceReady() {
				try {
					beacon_manager.startRanging(ALL_ESTIMOTE_BEACONS);
				} catch (RemoteException e) {
					// Something terrible happened. Help.
					Toast.makeText(ScanActivity.this, "Can't scan, something bad happened :(", Toast.LENGTH_LONG).show();
					Log.e(TAG, "Could not range: ", e);
				}
			}
			
		});
	}
	
	/* 
	 * A custom class to handle the averaging of the last 5 distance estimates.
	 * The main purpose of this is to form a smoothing effect, so that one high/low 
	 * ping from the beacon won't throw the distance off suddenly.
	 */
	private class BeaconDistanceTracker {
		private Queue<Double> distances;
		
		public BeaconDistanceTracker() {
			this.distances = new LinkedList<Double>();
		}
		
		// adds the latest distance to the queue, remove the oldest if there's more than 5
		public void addDistance(Double distance) {
			distances.add(distance);
			if (distances.size() > 5) {
				distances.remove();
			}
		}
		
		// take the average of all distances in the queue
		public Double getDistance() {
			int divisor = distances.size();
			if (divisor == 0) {
				return 0.0;
			} else {
				Double total = 0.0;
				for (Double d : distances) {
					total += d;
				}
				return total/divisor;
			}
		}
	}
	
	// Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<Beacon> beacons;
        private BeaconDistanceTracker[] distances;
        private LayoutInflater inflator;
 
        public LeDeviceListAdapter() {
            super();
            this.beacons = new ArrayList<Beacon>();
            this.inflator = ScanActivity.this.getLayoutInflater();
            
            // Initialize the classes for tracking distances - we are using 3 beacons 
            BeaconDistanceTracker beacon1_distance = new BeaconDistanceTracker();
            BeaconDistanceTracker beacon2_distance = new BeaconDistanceTracker();
            BeaconDistanceTracker beacon3_distance = new BeaconDistanceTracker();
            
            distances = new BeaconDistanceTracker[] {beacon1_distance, beacon2_distance, beacon3_distance};
        }
 
        // This function is called whenever a beacon scan returns a list of beacons
        public void replaceWith(Collection<Beacon> newBeacons) {
        	this.beacons.clear();
            this.beacons.addAll(newBeacons);
            // Sort the beacons based on their Major int identifier
            Collections.sort(beacons, new Comparator<Beacon>() {
              @Override
              public int compare(Beacon lhs, Beacon rhs) {
                return lhs.getMajor() - rhs.getMajor();
              }
            });
            nearestBeacon = 0;
            // Add the new distance to the distance tracker
            for (Beacon beacon : beacons) {
            	double distance;
            	
            	if (beacon.getMajor() == 32333) {
            		distances[0].addDistance(Utils.computeAccuracy(beacon));
        			distance = distances[0].getDistance();
        			if (distance < 2.0 && distance < distances[1].getDistance() && distance < distances[2].getDistance()) {
    					getActionBar().setSubtitle("In range of 32333");
    					nearestBeacon = 32333;
    				}
            	}
            	else if (beacon.getMajor() == 33771) {
            		distances[1].addDistance(Utils.computeAccuracy(beacon));
        			distance = distances[1].getDistance();
        			if (distance < 2.0 && distance < distances[0].getDistance() && distance < distances[2].getDistance()) {
    					getActionBar().setSubtitle("In range of 33771");
    					nearestBeacon = 33771;
    				}
            	}
            	else if (beacon.getMajor() == 50133) {
            		distances[2].addDistance(Utils.computeAccuracy(beacon));
        			distance = distances[2].getDistance();
        			if (distance < 2.0 && distance < distances[0].getDistance() && distance < distances[1].getDistance()) {
    					getActionBar().setSubtitle("In range of 50133");
    					nearestBeacon = 50133;
    				}
            	}
            }
            if (nearestBeacon == 0) {
            	getActionBar().setSubtitle("No Beacons in range");
            }
            
            notifyDataSetChanged();
        }
 
        @Override
        public int getCount() {
            return beacons.size();
        }
 
        @Override
        public Beacon getItem(int position) {
            return beacons.get(position);
        }
 
        @Override
        public long getItemId(int position) {
            return position;
        }
 
        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = inflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceDistance = (TextView) view.findViewById(R.id.device_distance);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }
 
            // Load the view with the values for each beacon
            Beacon beacon = beacons.get(i);
            final int identifier = beacon.getMajor();
            if (identifier > 0)
                viewHolder.deviceName.setText("Estimote " + Integer.toString(identifier));
            else
                viewHolder.deviceName.setText(R.string.unknown_device);
            switch (identifier) {
    			case 32333:
    				viewHolder.deviceDistance.setText(String.format("%.2f", distances[0].getDistance()));
    				break;
    			case 33771:
    				viewHolder.deviceDistance.setText(String.format("%.2f", distances[1].getDistance()));
    				break;
    			case 50133:
    				viewHolder.deviceDistance.setText(String.format("%.2f", distances[2].getDistance()));
    				break;
            }
 
            return view;
        }
        
        /*
         * Prints the location (x, y) of the sensor using d1 as the origin
         * Now with more actually working! 
         * Still need to take the distance as a ratio of the maximum, for best results.
         */
        /*
        public void GetCoordinates(Double d1, Double d2, Double d3) {
        	// Constants to make array indices more explicit
        	final int X = 0;
        	final int Y = 1;
        	// Declare our origin
        	double[] origin = {0.0d, 0.0d};
        	// Declare our sources (the estimotes)
        	double[] first = origin;
        	double[] second = {0.0d, 1.0d};
        	double[] third = {1.0d, 0.0d};
        	// Declare our coordinates
        	double[] sensor = {0.0d, 0.0d};
        	
        	double normalized1 = 0;
        	for (int j=0; j<2; j++) {
        		normalized1 += Math.pow(second[j] - first[j], 2.0);
        	}
        	
        	double[] ex = matrixDiv(matrixSub(second, first), Math.sqrt(normalized1));
        	double i = matrixDot(ex, matrixSub(third, first));
        	
        	double[] exMultI = matrixMult(ex, i);
        	double normalized2 = 0;
        	for (int j=0; j<2; j++) {
        		normalized2 += Math.pow(third[j] - first[j] - exMultI[j], 2.0);
        	}
        	
        	double[] ey = matrixDiv(matrixSub(matrixSub(third, first), matrixMult(ex, i)), Math.sqrt(normalized2));
        	
        	double d = Math.sqrt(normalized1);
        	
        	double j = matrixDot(ey, matrixSub(third, first));
        	
        	sensor[X] = (d1*d1 - d2*d2 + d*d) / (2*d);
        	sensor[Y] = ((d1*d1 - d3*d3 + i*i + j*j) / (2*j)) - ((i/j*sensor[X]));
        	
        	getActionBar().setSubtitle("x: " + String.format("%.1f", sensor[X]) + ", y: " + String.format("%.1f", sensor[Y]));
        	Log.d(TAG, "x: " + String.format("%.1f", sensor[X]) + ", y: " + String.format("%.1f", sensor[Y]));
        } */
        
        /*
         * Helper function to do basic matrix subtractions
         */
        /*
        public double[] matrixSub(double[] a, double[] b) {
        	double[] result = {0.0d, 0.0d};
        	for (int i=0; i<2; i++) {
        		result[i] = a[i] - b[i];
        	}
        	return result;
        } */
        
        /*
         *  Helper function to do basic matrix divisions by a scalar
         */
        /*
        public double[] matrixDiv(double[] a, double scalar) {
        	double[] result = {0.0d, 0.0d};
        	for (int i=0; i<2; i++) {
        		result[i] = a[i]/scalar;
        	}
        	return result;
        } */
        
        /*
         * Helper function to do basic matrix multiplications by a scalar
         */
        /*
        public double[] matrixMult(double[] a, double scalar) {
        	double[] result = {0.0d, 0.0d};
        	for (int i=0; i<2; i++) {
        		result[i] = a[i]*scalar;
        	}
        	return result;
        } */
        
        /*
         * Helper function to do basic matrix dot products
         */
        /*
        public double matrixDot(double[] a, double[] b) {
        	double result = 0.0d;
        	for (int i=0; i<2; i++) {
        		result += a[i] * b[i];
        	}
        	return result;
        } */
    }
    
    static class ViewHolder {
        TextView deviceName;
        TextView deviceDistance;
    }
    
    private class SendStateAPITask extends AsyncTask<Void, Void, String> {

		@Override
		protected String doInBackground(Void... urls) {
			String requestData;
			if (lightOn) {
				requestData = "{\"on\": false}";
			} else {
				requestData = "{\"on\": true}";
			}
			String urlString = "http://" + bridgeIP + "/api/" + hueAPIKey + "/lights/" + bulbNumber + "/state";
			try {
				URL url = new URL(urlString);
				HttpURLConnection con = (HttpURLConnection) url.openConnection();
				con.setDoOutput(true);
				con.setRequestMethod("PUT");
				OutputStreamWriter out = new OutputStreamWriter(con.getOutputStream());
				out.write(requestData);
				out.close();
				int response = con.getResponseCode();
				Log.d(TAG, "SET COLOR RESPONSE: " + response);
				con.getInputStream();
			} catch (MalformedURLException e) {
				Log.e(TAG, "Malformed URL: " + e);
			} catch (IOException e) {
				Log.e(TAG, "SendStateAPITask IOException: " + e);
			}
			return null;
		}
    }
    
    private class SendColorStateAPITask extends AsyncTask<Void, Void, String> {

		@Override
		protected String doInBackground(Void... urls) {
			String requestData;
			Log.d(TAG, "nearestBeacon: " + nearestBeacon);
			if (nearestBeacon == 32333) {
				//Blue
				requestData = "{\"hue\": 45745, \"bri\": 254, \"sat\": 254}";
			} else if (nearestBeacon == 33771) {
				//Red
				requestData = "{\"hue\": 65000, \"bri\": 254, \"sat\": 254}";
			} else if (nearestBeacon == 50133) {
				//Pink
				requestData = "{\"hue\": 55212, \"bri\": 254, \"sat\": 254}";
			} else {
				return null;
			}
			String urlString = "http://" + bridgeIP + "/api/" + hueAPIKey + "/lights/" + bulbNumber + "/state";
			try {
				URL url = new URL(urlString);
				HttpURLConnection con = (HttpURLConnection) url.openConnection();
				con.setDoOutput(true);
				con.setRequestMethod("PUT");
				OutputStreamWriter out = new OutputStreamWriter(con.getOutputStream());
				out.write(requestData);
				out.close();
				int response = con.getResponseCode();
				Log.d(TAG, "SET COLOR RESPONSE: " + response);
				con.getInputStream();
			} catch (MalformedURLException e) {
				Log.e(TAG, "Malformed URL: " + e);
			} catch (IOException e) {
				Log.e(TAG, "SendColorStateAPITask IOException: " + e);
			}
			return null;
		}
    }
    
    private class FetchAPITask extends AsyncTask<Void, Void, String> {

		@Override
		protected String doInBackground(Void... urls) {
			try {
				return fetchStateFromAPI(bulbNumber);
			} catch (IOException e) {
				Log.e(TAG, "FetchAPITask IOException: " + e);
			}
			return null;
		}
		
		@Override
		protected void onPostExecute(String result) {
			//Log.d(TAG, "The string is: " + result);
			if (result == null) {
				return;
			}
			JSONObject jObject = null;
			try {
				jObject = new JSONObject(result);
			} catch (JSONException e) {
				Log.e(TAG, "Error converting result: " + e);
			}
			//Log.d(TAG, "The json object is: " + jObject.toString());
			
			JSONObject lightState = null;
			try {
				lightState = jObject.getJSONObject("state");
				try {
					String state = lightState.getString("on");
					//Log.d(TAG, "Light state: " + state);
					if (state.equals("true")) {
						lightOn = true;
						stateButton.setText("Off");
					} else {
						lightOn = false;
						stateButton.setText("On");
					}
				} catch (JSONException e) {
					Log.e(TAG, "Error converting result: " + e);
				}
			} catch (JSONException e) {
				Log.e(TAG, "Error converting result: " + e);
			}
		}
	}
	
	private String fetchStateFromAPI(int bulb) throws IOException {
		String myUrl = "http://" + bridgeIP + "/api/" + hueAPIKey + "/lights/" + bulb;
		//Log.d(TAG, "URL: " + myUrl);
		InputStream is = null;
		try {
			URL url = new URL(myUrl);
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setReadTimeout(2000);
			con.setConnectTimeout(2000);
			con.setRequestMethod("GET");
			con.setDoInput(true);
			int response = con.getResponseCode();
			Log.d(TAG, "FETCH STATE RESPONSE: " + response);
			is = con.getInputStream();
			
			String content = readIt(is);
			return content;
		} finally {
			if (is != null) {
				is.close();
			}
		}
	}
	
	public String readIt(InputStream stream) throws IOException, UnsupportedEncodingException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int length = 0;
		while ((length = stream.read(buffer)) != -1) {
			baos.write(buffer, 0, length);
		}
		return new String(baos.toByteArray(), "UTF-8");
	}
}
