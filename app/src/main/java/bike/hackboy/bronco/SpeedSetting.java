package bike.hackboy.bronco;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.navigation.fragment.NavHostFragment;

import bike.hackboy.bronco.data.Command;
import bike.hackboy.bronco.data.Uuid;
import bike.hackboy.bronco.utils.Converter;
import bike.hackboy.bronco.utils.FlashWriter;

public class SpeedSetting extends Fragment {
	private int speed = 25;
	private int motorMode = 0;
	private boolean commitWrite = false;

	private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
		String event = intent.getStringExtra("event");

		if (!event.equals("on-characteristic-read")) return;
		if (!intent.getStringExtra("uuid").equals(Uuid.characteristicSettingsReadString))
			return;

		byte[] value = intent.getByteArrayExtra("value");
		int service = value[0];
		int operation = value[1];

		switch (service) {
			case 0x0a:
				Log.w("foo", "received speed setting");

				SpeedSetting.this.speed = value[4];
				updateView();
			break;
			case 0x1:
				switch(operation) {
					case 0x10: // write notification
						LocalBroadcastManager.getInstance(requireContext())
							.sendBroadcast(new Intent(BuildConfig.APPLICATION_ID)
								.putExtra("event", "read-motor-mode"));

					break;
					case 0x3: // read notification
						SpeedSetting.this.motorMode = value[4];
						updateView();

						if (commitWrite) {
							Log.w("write_flash", "in write flash");
							Log.w("write_flash", Converter.byteArrayToHexString(value));
							writeFlash();
						}
					break;
				}
			break;
		}
		}
	};

	@Override
	public void onResume() {
		super.onResume();

		LocalBroadcastManager.getInstance(requireContext())
			.registerReceiver(messageReceiver, new IntentFilter(BuildConfig.APPLICATION_ID));
	}

	@Override
	public void onPause() {
		super.onPause();

		LocalBroadcastManager.getInstance(requireContext())
			.unregisterReceiver(messageReceiver);
	}

	@Override
	public View onCreateView(
		LayoutInflater inflater, ViewGroup container,
		Bundle savedInstanceState
	) {
		return inflater.inflate(R.layout.speed_setting, container, false);
	}

	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(requireContext());
		lbm.sendBroadcast(new Intent(BuildConfig.APPLICATION_ID).putExtra("event", "read-speed"));
		lbm.sendBroadcast(new Intent(BuildConfig.APPLICATION_ID).putExtra("event", "read-motor-mode"));

		view.findViewById(R.id.button_speed_apply).setOnClickListener(view1 -> {
			LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(
				new Intent(BuildConfig.APPLICATION_ID)
					.putExtra("event", "set-speed")
					.putExtra("value", speed)
			);

			NavHostFragment.findNavController(SpeedSetting.this).navigate(R.id.action_SpeedSetting_to_Dashboard);
		});

		view.findViewById(R.id.button_speed_cancel).setOnClickListener(view1 -> NavHostFragment
			.findNavController(SpeedSetting.this)
			.navigate(R.id.action_SpeedSetting_to_Dashboard));

		view.findViewById(R.id.button_speed_cancel_large).setOnClickListener(view2 -> NavHostFragment
			.findNavController(SpeedSetting.this)
			.navigate(R.id.action_SpeedSetting_to_Dashboard));

		view.findViewById(R.id.button_disable_limit).setOnClickListener(view3 -> {
			new AlertDialog.Builder(requireContext(), R.style.Theme_Bronco_AlertDialogWarning)
				.setTitle(R.string.caution_advanced)
				.setMessage(R.string.disable_speed_limit_explanation)
				.setNegativeButton(R.string.abort, null)
				.setPositiveButton(R.string.proceed, (dialog, whichButton) -> {
					dialog.dismiss();

					commitWrite = true;

					lbm.sendBroadcast(new Intent(BuildConfig.APPLICATION_ID)
						.putExtra("event", "set-motor-mode-torque"));
				})
				.show();
		});

		view.findViewById(R.id.button_enable_limit).setOnClickListener(view4 -> {
			commitWrite = true;

			lbm.sendBroadcast(new Intent(BuildConfig.APPLICATION_ID)
				.putExtra("event", "set-motor-mode-torque-with-limit"));
		});

		SeekBar slider = view.findViewById(R.id.max_speed_bar);
		slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				TextView kph = view.findViewById(R.id.max_speed_value);
				speed = 25 + progress;
				kph.setText(String.valueOf(speed));
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});
	}

	private void updateView() {
		final int MOTOR_UNRESTRICTED = 1;
		final int MOTOR_RESTRICTED = 2;

		View view = requireView();

		try {
			switch(motorMode) {
				case MOTOR_UNRESTRICTED:
					view.findViewById(R.id.unrestricted_layout).setVisibility(View.VISIBLE);
					view.findViewById(R.id.restricted_layout).setVisibility(View.INVISIBLE);
				break;
				case MOTOR_RESTRICTED:
					((SeekBar) view.findViewById(R.id.max_speed_bar)).setProgress(speed - 25);
					view.findViewById(R.id.unrestricted_layout).setVisibility(View.INVISIBLE);
					view.findViewById(R.id.restricted_layout).setVisibility(View.VISIBLE);
				break;
			}
		} catch(Exception ignored) { }
	}

	private void writeFlash() {
		// GC should clean this once it's called again
		commitWrite = false;
		FlashWriter fw = new FlashWriter(requireContext());
		fw.run();
	}
}