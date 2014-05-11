package com.jeremyutting.localactions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

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
	private static final Region ALL_ESTIMOTE_BEACONS = new Region("rid", null, null, null);
	private BeaconManager beacon_manager;
	private LeDeviceListAdapter adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_scan);
		
		L.enableDebugLogging(true);
		adapter = new LeDeviceListAdapter();
		ListView list = (ListView) findViewById(R.id.device_list);
		list.setAdapter(adapter);
		
		beacon_manager = new BeaconManager(this);
		beacon_manager.setForegroundScanPeriod(200, 0);
		beacon_manager.setRangingListener(new BeaconManager.RangingListener() {
			
			@Override
			public void onBeaconsDiscovered(Region region, final List<Beacon> beacons) {
				runOnUiThread(new Runnable() {

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
	      connectToService();
	    }
	}
	
	@Override
	protected void onStop() {
		try {
			beacon_manager.stopRanging(ALL_ESTIMOTE_BEACONS);
		} catch (RemoteException e) {
			Log.d(TAG, "Error while stopping ranging", e);
		}
		super.onStop();
	}
	
	private void connectToService() {
		getActionBar().setSubtitle("Scanning...");
		adapter.replaceWith(Collections.<Beacon>emptyList());
		beacon_manager.connect(new BeaconManager.ServiceReadyCallback() {

			@Override
			public void onServiceReady() {
				try {
					beacon_manager.startRanging(ALL_ESTIMOTE_BEACONS);
				} catch (RemoteException e) {
					Toast.makeText(ScanActivity.this, "Can't scan, something bad happened :(", Toast.LENGTH_LONG).show();
					Log.e(TAG, "Could not range: ", e);
				}
			}
			
		});
	}
	
	// A custom class to handle the averaging of the last 5 distance estimates
	private class BeaconDistanceTracker {
		private Queue<Double> distances;
		
		public BeaconDistanceTracker() {
			this.distances = new LinkedList<Double>();
		}
		
		public void addDistance(Double distance) {
			distances.add(distance);
			if (distances.size() > 5) {
				distances.remove();
			}
		}
		
		public Double getDistance() {
			int divisor = distances.size();
			Double total = 0.0;
			for (Double d : distances) {
				total += d;
			}
			return total/divisor;
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
 
        public void replaceWith(Collection<Beacon> newBeacons) {
        	this.beacons.clear();
            this.beacons.addAll(newBeacons);
            Collections.sort(beacons, new Comparator<Beacon>() {
              @Override
              public int compare(Beacon lhs, Beacon rhs) {
                return lhs.getMajor() - rhs.getMajor();
              }
            });
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
        	Log.d(TAG, "getView()");
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = inflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }
 
            Beacon beacon = beacons.get(i);
            final int identifier = beacon.getMajor();
            if (identifier > 0)
                viewHolder.deviceName.setText("Estimote " + Integer.toString(identifier));
            else
                viewHolder.deviceName.setText(R.string.unknown_device);
            switch (identifier) {
    			case 32333:
    				viewHolder.deviceAddress.setText(String.format("%.2f", distances[0].getDistance()));
    				break;
    			case 33771:
    				viewHolder.deviceAddress.setText(String.format("%.2f", distances[1].getDistance()));
    				break;
    			case 50133:
    				viewHolder.deviceAddress.setText(String.format("%.2f", distances[2].getDistance()));
    				break;
            }
 
            return view;
        }
    }
    
    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}
