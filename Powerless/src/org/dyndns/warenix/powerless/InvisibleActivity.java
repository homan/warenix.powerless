package org.dyndns.warenix.powerless;

import org.dyndns.warenix.powerless.PowerlessActivity.MyDeviceAdminReceiver;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class InvisibleActivity extends Activity {
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		parseAction();
		finish();
	}

	protected boolean parseAction() {
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			if (PowerlessActivity.ACTION_LOCK_SCREEN.equals(extras
					.getString(PowerlessActivity.EXTRA_ACTION))) {
				onLockscreenClicked(null);
				return true;
			}
		}
		return false;
	}

	private void onLockscreenClicked(View v) {
		DevicePolicyManager devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
		ComponentName adminComponent = new ComponentName(
				InvisibleActivity.this, MyDeviceAdminReceiver.class);

		if (!devicePolicyManager.isAdminActive(adminComponent)) {
			Intent intent = new Intent(
					DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
			intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
					adminComponent);
			startActivityForResult(intent, 1);
		} else {
			devicePolicyManager.lockNow();
			finish();
		}

	}

}
