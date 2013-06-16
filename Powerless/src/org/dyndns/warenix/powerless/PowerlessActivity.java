package org.dyndns.warenix.powerless;

import java.io.File;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * Perform functions of a hardware power button.
 * 
 * @author warenix
 * 
 */
public class PowerlessActivity extends FragmentActivity implements
		OnClickListener {

	private static final String TAG = "PowerlessActivity";

	public static final String EXTRA_ACTION = "action";
	public static final String ACTION_LOCK_SCREEN = "lock_screen";

	PowerManager mPm;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mPm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
		if (mPm == null) {
			onInitError("cannot init Power Manager");
			return;
		}
		setupUI();
		showNotification();
	}

	ImageView screenshot;

	public void onResume() {
		super.onResume();

		if (parseAction()) {
			finish();
		} else {
			previewScreenshot();
		}
	}

	private void previewScreenshot() {
		Log.d(TAG, "set preview screenshot");
		screenshot.setImageBitmap(null);
		screenshot.setImageURI(Uri.fromFile(new File(getScreenshotFile())));
	}

	protected boolean parseAction() {
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			if (ACTION_LOCK_SCREEN.equals(extras.getString(EXTRA_ACTION))) {
				onLockscreenClicked(null);
				return true;
			}
		}
		return false;
	}

	private void onInitError(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	}

	protected void setupUI() {
		screenshot = (ImageView) findViewById(R.id.screenshot);

		int[] buttonIds = new int[] { R.id.recovery,
				// R.id.fastboot,
				R.id.reboot, R.id.lock_screen, R.id.take_screenshot };
		for (int id : buttonIds) {
			((Button) this.findViewById(id)).setOnClickListener(this);
		}

	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
		case R.id.reboot:
			onRebootClicked(view);
			break;
		case R.id.recovery:
			onRecoveryClicked(view);
			break;
		// case R.id.fastboot:
		// onFastbootClicked(view);
		// break;
		case R.id.lock_screen:
			onLockscreenClicked(view);
			break;
		case R.id.take_screenshot:
			onTakeScreenshotClicked(view);
			break;
		}
	}

	private void onRebootClicked(View v) {
		runCommandAsRoot("reboot");
	}

	private void onRecoveryClicked(View v) {
		runCommandAsRoot("reboot recovery");
	}

	// private void onFastbootClicked(View v) {
	// runCommandAsRoot("reboot fastboot");
	// }

	private void onLockscreenClicked(View v) {
		DevicePolicyManager devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
		ComponentName adminComponent = new ComponentName(
				PowerlessActivity.this, MyDeviceAdminReceiver.class);

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

	private void onTakeScreenshotClicked(View v) {
		startProximitySensor();
	}

	SensorManager mSensorManager;
	Sensor mProximitySensor;
	boolean mIsProximityStarted;

	private void startProximitySensor() {
		getApplicationContext();
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mProximitySensor = mSensorManager
				.getDefaultSensor(Sensor.TYPE_PROXIMITY);
		if (mProximitySensor == null) {
			Log.i(TAG, "No proximity sensor found!");
		} else {
			mIsProximityStarted = true;
			Log.i(TAG, "Proximity started");
			mSensorManager.registerListener(proximitySensorEventListener,
					mProximitySensor, SensorManager.SENSOR_DELAY_UI);
		}
	}

	private SensorEventListener proximitySensorEventListener = new SensorEventListener() {

		static final int SENSOR_COVERED = 0;

		@Override
		public void onSensorChanged(SensorEvent event) {
			if (Sensor.TYPE_PROXIMITY == event.sensor.getType()) {
				Log.i(TAG,
						"Proximity Sensor Reading: "
								+ String.valueOf(event.values[0]));

				if (SENSOR_COVERED == event.values[0]) {
					if (mIsProximityStarted) {
						mIsProximityStarted = false;
						mSensorManager.unregisterListener(this);
					}

					takeScreenshot();
				}
			}

		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {

		}
	};

	private void takeScreenshot() {
		String screenshotFullPath = getScreenshotFile();
		String command = String.format(
				"sleep 3 && /system/bin/screencap -p %s", screenshotFullPath);

		runCommandAsRoot(command);
		Toast.makeText(this, "Captured", Toast.LENGTH_SHORT).show();
		Log.d(TAG, "Captured");

		previewScreenshot();
	}

	String getScreenshotFile() {
		File cacheDir = this.getExternalCacheDir();
		String screenshotfullPath = String.format("%s/%s",
				cacheDir.getAbsoluteFile(), "screenshot.png");
		return screenshotfullPath;
	}

	protected void runCommandAsRoot(String command) {
		try {
			Process proc = Runtime.getRuntime().exec(
					new String[] { "su", "-c", command });
			proc.waitFor();
		} catch (Exception ex) {
			Log.i(TAG, "Could not reboot", ex);
		}
	}

	private NotificationManager mNotificationManager;
	private int SIMPLE_NOTFICATION_ID = 1;
	private Notification notifyDetails;

	public void showNotification() {
		Context context = getApplicationContext();
		CharSequence contentTitle = "Powerless";
		CharSequence contentText = "Tape me to lock screen now.";
		Intent notifyIntent = new Intent(this, PowerlessActivity.class);
		notifyIntent.putExtra(EXTRA_ACTION, ACTION_LOCK_SCREEN);
		PendingIntent intent = PendingIntent.getActivity(this, 0, notifyIntent,
				android.content.Intent.FLAG_ACTIVITY_NEW_TASK);

		if (mNotificationManager == null) {
			String ns = Context.NOTIFICATION_SERVICE;
			mNotificationManager = (NotificationManager) getSystemService(ns);
		}
		if (notifyDetails == null) {
			int icon = R.drawable.ic_launcher;
			CharSequence tickerText = "Powerless is running.";
			long when = System.currentTimeMillis();

			notifyDetails = new Notification(icon, tickerText, when);
		}
		notifyDetails.setLatestEventInfo(context, contentTitle, contentText,
				intent);
		mNotificationManager.notify(SIMPLE_NOTFICATION_ID, notifyDetails);

	}

	public static class MyDeviceAdminReceiver extends DeviceAdminReceiver {

	}
}