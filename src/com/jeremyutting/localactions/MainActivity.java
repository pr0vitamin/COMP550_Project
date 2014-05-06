package com.jeremyutting.localactions;

import android.os.Bundle;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.view.Menu;

public class MainActivity extends Activity {
	
	private final static int REQUEST_ENABLE_BT = 1;
	private BluetoothAdapter bt_adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Set up BLE, get adapter
		final BluetoothManager bt_manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		bt_adapter = bt_manager.getAdapter();
		
		// Is BLE available? Enabled?
		if (bt_adapter == null || !bt_adapter.isEnabled()) {
			Intent enable_bt_intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enable_bt_intent, REQUEST_ENABLE_BT);
		}
		
		setContentView(R.layout.activity_main);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}
