package com.jeremyutting.localactions;

import android.os.Bundle;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.view.Menu;
import android.view.View;

public class MainActivity extends Activity {
	
	private final static int REQUEST_ENABLE_BT = 1;
	private BluetoothAdapter bt_adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// Set up BLE, get adapter
		final BluetoothManager bt_manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		bt_adapter = bt_manager.getAdapter();
		
		// Is BLE available? Enabled?
		if (bt_adapter == null || !bt_adapter.isEnabled()) {
			// Ask for BlueTooth to be turned on
			Intent enable_bt_intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enable_bt_intent, REQUEST_ENABLE_BT);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	// This is called when the start button is pressed - it launches the scan activity
	public void launchScan(View view) {
		Intent intent = new Intent(this, ScanActivity.class);
		startActivity(intent);
	}

}
