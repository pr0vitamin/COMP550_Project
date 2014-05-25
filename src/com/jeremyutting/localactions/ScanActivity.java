package com.jeremyutting.localactions;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.estimote.sdk.Utils;
import com.estimote.sdk.utils.L;

import android.os.Bundle;
import android.os.RemoteException;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v4.app.NavUtils;

public class ScanActivity extends Activity {
	
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
		
		L.enableDebugLogging(false);
		
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
						getActionBar().setSubtitle("Found " + beacons.size() + " beacons");
						adapter.replaceWith(beacons);
					}
				});
			}
		});
		
		// Show the Up button in the action bar.
		setupActionBar();
	}

	/**
	 * Set up the {@link android.app.ActionBar}.
	 */
	private void setupActionBar() {

		getActionBar().setDisplayHomeAsUpEnabled(true);

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
            // Add the new distance to the distance tracker
            for (Beacon beacon : beacons) {
            	switch (beacon.getMajor()) {
            		case 32333:
            			distances[0].addDistance(Utils.computeAccuracy(beacon));
            			break;
            		case 33771:
            			distances[1].addDistance(Utils.computeAccuracy(beacon));
            			break;
            		case 50133:
            			distances[2].addDistance(Utils.computeAccuracy(beacon));
            			break;
            	}
            }
            
            // This is where we want to do the trilateration.
            GetCoordinates(distances[0].getDistance(), distances[1].getDistance(), distances[2].getDistance());
            
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
         * This is my poor attempt at Trilateration.
         * 
         * This function doesn't really work at the moment.
         */
        public void GetCoordinates(Double d1, Double d2, Double d3) {
        	// Coordinated of the beacons, this is static and arbitrary.
        	int b1x = 11;
        	int b1y = 21;
        	int b2x = 10;
        	int b2y = 10;
        	int b3x = 21;
        	int b3y = 11;
        	
        	// Here be dragons
        	Double W, Z, x, y, y2;
        	W = (d1*d1) - (d2*d2) - (b1x*b1x) - (b1y*b1y) + (b2x*b2x) + (b2y*b2y);
        	Z = (d2*d2) - (d3*d3) - (b2x*b2x) - (b2y*b2y) + (b3x*b3x) + (b3y*b3y);
        	
        	x = (W*(b3y-b2y) - Z*(b2y-b1y)) / (2*((b2x-b1x)*(b3y-b2y) - (b3x-b2x)*(b2y-b1y)));
        	y = (W - 2*x*(b2x-b1x)) / (2*(b2y-b1y));
        	y2 = (Z - 2*x*(b3x-b2x)) / (2*(b3y-b2y));
        	
        	y = (y+y2) / 2;
        	
        	// Log the calculate x/y coordinates to the debug terminal
        	//Log.d(TAG, "x: " + String.format("%.1f", x));
        	//Log.d(TAG, "y: " + String.format("%.1f", y));
        }
    }
    
    static class ViewHolder {
        TextView deviceName;
        TextView deviceDistance;
    }
    
    public static LightState parseState(JSONObject jsonObj) {

		LightState state = new LightState();

		try{
			JSONArray state_list = jsonObj.getJSONArray("state");
			JSONObject stat = state_list.getJSONObject(0);
			state.hue = stat.optInt("hue");
			state.on = stat.optBoolean("on");
			state.brightness = stat.optInt("bri");
			state.saturation = stat.optInt("sat");

		} catch(JSONException e) {
			Log.d(TAG, "JSON exception when parsing light state");
		}
		return state;
	}
    
    public static void parseSetStateResponse(URI url, int bri,int hue,int sat, boolean on) {
    	try {
    		// 1. create HttpClient
			HttpClient httpclient = new DefaultHttpClient();

			// 2. make POST request to the given URL
			HttpPost httpPost = new HttpPost(url);

			String json = "";


			JSONObject jsonObject = new JSONObject();

			jsonObject.accumulate("hue", hue);
			jsonObject.accumulate("on", on);
			jsonObject.accumulate("bri", bri);
			jsonObject.accumulate("sat", sat);


			json = jsonObject.toString();
			Log.d(TAG, "json: " + json);

			// 5. set json to StringEntity
			StringEntity se = new StringEntity(json);

			// 6. set httpPost Entity
			httpPost.setEntity(se);

			// 7. Set some headers to inform server about the type of the content   
			httpPost.setHeader("Accept", "application/json");
			httpPost.setHeader("Content-type", "application/json");

			// 8. Execute POST request to the given URL
			HttpResponse httpResponse = httpclient.execute(httpPost);
			
		} catch (Exception e) {
			Log.d(TAG, "Stack trace: " + e);
    	}
	}
    
    public void lightOnOff(View view) {
    	URI uri = null;
		try {
			uri = new URI("http://192.168.99.161/");
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	parseSetStateResponse(uri, 1, 2, 3, true);
    }
    
    static class LightState {
    	public int hue;
    	public boolean on;
    	public int brightness;
    	public int saturation;
    }
}
