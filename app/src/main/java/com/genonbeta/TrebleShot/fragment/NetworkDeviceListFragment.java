package com.genonbeta.TrebleShot.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.genonbeta.TrebleShot.R;
import com.genonbeta.TrebleShot.adapter.NetworkDeviceListAdapter;
import com.genonbeta.TrebleShot.app.RecyclerViewFragment;
import com.genonbeta.TrebleShot.config.AppConfig;
import com.genonbeta.TrebleShot.database.AccessDatabase;
import com.genonbeta.TrebleShot.dialog.DeviceInfoDialog;
import com.genonbeta.TrebleShot.object.NetworkDevice;
import com.genonbeta.TrebleShot.service.DeviceScannerService;
import com.genonbeta.TrebleShot.ui.UIConnectionUtils;
import com.genonbeta.TrebleShot.ui.callback.DetachListener;
import com.genonbeta.TrebleShot.ui.callback.NetworkDeviceSelectedListener;
import com.genonbeta.TrebleShot.ui.callback.TitleSupport;
import com.genonbeta.TrebleShot.util.AppUtils;
import com.genonbeta.TrebleShot.util.ConnectionUtils;
import com.genonbeta.TrebleShot.util.NsdDiscovery;
import com.genonbeta.TrebleShot.widget.RecyclerViewAdapter;

public class NetworkDeviceListFragment
		extends RecyclerViewFragment<NetworkDevice, RecyclerViewAdapter.ViewHolder, NetworkDeviceListAdapter>
		implements TitleSupport, DetachListener
{
	public static final int REQUEST_LOCATION_PERMISSION = 643;

	private NsdDiscovery mNsdDiscovery;
	private NetworkDeviceSelectedListener mDeviceSelectedListener;
	private IntentFilter mIntentFilter = new IntentFilter();
	private StatusReceiver mStatusReceiver = new StatusReceiver();
	private SwipeRefreshLayout mSwipeRefreshLayout;
	private UIConnectionUtils mConnectionUtils;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		mIntentFilter.addAction(DeviceScannerService.ACTION_SCAN_STARTED);
		mIntentFilter.addAction(DeviceScannerService.ACTION_DEVICE_SCAN_COMPLETED);
		mIntentFilter.addAction(AccessDatabase.ACTION_DATABASE_CHANGE);
		mIntentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
		mIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);

		mNsdDiscovery = new NsdDiscovery(getContext(), getDatabase(), getDefaultPreferences());
	}

	@Override
	protected RecyclerView onListView(View mainContainer, ViewGroup listViewContainer)
	{
		Context context = mainContainer.getContext();

		mSwipeRefreshLayout = new SwipeRefreshLayout(context);

		mSwipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(context, R.color.colorPrimary),
				ContextCompat.getColor(context, R.color.colorAccent));

		mSwipeRefreshLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		listViewContainer.addView(mSwipeRefreshLayout);

		return super.onListView(mainContainer, mSwipeRefreshLayout);
	}

	@Override
	public void onViewCreated(@NonNull View view, final Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);

		setEmptyImage(R.drawable.ic_devices_white_24dp);
		setEmptyText(getString(R.string.text_findDevicesHint));

		useEmptyActionButton(getString(R.string.butn_scan), new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				requestRefresh();
			}
		});

		mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener()
		{
			@Override
			public void onRefresh()
			{
				requestRefresh();
			}
		});
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		setHasOptionsMenu(true);

		if (getDefaultPreferences().getBoolean("scan_devices_auto", false))
			requestRefresh();
	}

	@Override
	public NetworkDeviceListAdapter onAdapter()
	{
		final AppUtils.QuickActions<RecyclerViewAdapter.ViewHolder> quickActions = new AppUtils.QuickActions<RecyclerViewAdapter.ViewHolder>()
		{
			@Override
			public void onQuickActions(final RecyclerViewAdapter.ViewHolder clazz)
			{
				clazz.getView().setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View v)
					{
						final NetworkDevice device = getAdapter().getList().get(clazz.getAdapterPosition());

						if (mDeviceSelectedListener != null
								&& device.versionNumber != -1
								&& device.versionNumber < AppConfig.SUPPORTED_MIN_VERSION) {
							createSnackbar(R.string.mesg_versionNotSupported)
									.show();
						} else if (mDeviceSelectedListener == null
								|| !mDeviceSelectedListener.onNetworkDeviceSelected(device, null)) {
							if (device instanceof NetworkDeviceListAdapter.HotspotNetwork) {
								final NetworkDeviceListAdapter.HotspotNetwork hotspotNetwork = (NetworkDeviceListAdapter.HotspotNetwork) device;

								AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

								builder.setTitle(hotspotNetwork.nickname);
								builder.setMessage(R.string.text_trebleshotHotspotDescription);
								builder.setNegativeButton(R.string.butn_close, null);
								builder.setPositiveButton(getConnectionUtils().isConnectedToNetwork(hotspotNetwork) ? R.string.butn_disconnect : R.string.butn_connect, new DialogInterface.OnClickListener()
								{
									@Override
									public void onClick(DialogInterface dialog, int which)
									{
										getConnectionUtils().toggleConnection(hotspotNetwork);
									}
								});

								builder.show();
							} else if (device.brand != null && device.model != null)
								new DeviceInfoDialog(getActivity(), getDatabase(), getDefaultPreferences(), device).show();
						}
					}
				});
			}
		};

		return new NetworkDeviceListAdapter(getDatabase(), getDefaultPreferences(), getConnectionUtils())
		{
			@NonNull
			@Override
			public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
			{
				return AppUtils.quickAction(super.onCreateViewHolder(parent, viewType), quickActions);
			}
		};
	}

	@Override
	public void onResume()
	{
		super.onResume();
		getActivity().registerReceiver(mStatusReceiver, mIntentFilter);

		refreshList();
		checkRefreshing();

		mNsdDiscovery.startDiscovering();
	}

	@Override
	public void onPause()
	{
		super.onPause();
		getActivity().unregisterReceiver(mStatusReceiver);

		mNsdDiscovery.stopDiscovering();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.actions_network_device, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId()) {
			case R.id.network_devices_scan:
				requestRefresh();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (REQUEST_LOCATION_PERMISSION == requestCode)
			getUIConnectionUtils().showConnectionOptions(getActivity(), REQUEST_LOCATION_PERMISSION);
	}

	@Override
	public void onPrepareDetach()
	{
		showCustomView(false);
	}

	public void checkRefreshing()
	{
		mSwipeRefreshLayout.setRefreshing(!DeviceScannerService
				.getDeviceScanner()
				.isScannerAvailable());
	}

	public ConnectionUtils getConnectionUtils()
	{
		return getUIConnectionUtils().getConnectionUtils();
	}

	public UIConnectionUtils getUIConnectionUtils()
	{
		if (mConnectionUtils == null)
			mConnectionUtils = new UIConnectionUtils(ConnectionUtils.getInstance(getContext()), this);

		return mConnectionUtils;
	}

	@Override
	public CharSequence getTitle(Context context)
	{
		return context.getString(R.string.text_deviceList);
	}

	public void requestRefresh()
	{
		getConnectionUtils().getWifiManager().startScan();

		if (DeviceScannerService.getDeviceScanner().isScannerAvailable())
			getContext().startService(new Intent(getContext(), DeviceScannerService.class)
					.setAction(DeviceScannerService.ACTION_SCAN_DEVICES));
		else {
			Toast.makeText(getContext(), R.string.mesg_stopping, Toast.LENGTH_SHORT).show();

			DeviceScannerService
					.getDeviceScanner()
					.interrupt();
		}
	}

	public void setDeviceSelectedListener(NetworkDeviceSelectedListener listener)
	{
		mDeviceSelectedListener = listener;
	}

	private class StatusReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			checkRefreshing();

			if (DeviceScannerService.ACTION_SCAN_STARTED.equals(intent.getAction()) && intent.hasExtra(DeviceScannerService.EXTRA_SCAN_STATUS)) {
				String scanStatus = intent.getStringExtra(DeviceScannerService.EXTRA_SCAN_STATUS);

				if (DeviceScannerService.STATUS_OK.equals(scanStatus) || DeviceScannerService.SCANNER_NOT_AVAILABLE.equals(scanStatus)) {
					boolean selfNetwork = getConnectionUtils().isConnectionSelfNetwork();

					if (!selfNetwork)
						createSnackbar(DeviceScannerService.STATUS_OK.equals(scanStatus) ?
								R.string.mesg_scanningDevices
								: R.string.mesg_stillScanning)
								.show();
					else
						createSnackbar(R.string.mesg_scanningDevicesSelfHotspot)
								.setAction(R.string.butn_disconnect, new View.OnClickListener()
								{
									@Override
									public void onClick(View v)
									{
										getConnectionUtils().getWifiManager().disconnect();
									}
								})
								.show();
				} else if (DeviceScannerService.STATUS_NO_NETWORK_INTERFACE.equals(scanStatus))
					getUIConnectionUtils().showConnectionOptions(getActivity(), REQUEST_LOCATION_PERMISSION);
			} else if (DeviceScannerService.ACTION_DEVICE_SCAN_COMPLETED.equals(intent.getAction())) {
				createSnackbar(R.string.mesg_scanCompleted)
						.show();
			} else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())
					|| (AccessDatabase.ACTION_DATABASE_CHANGE.equals(intent.getAction())
					&& AccessDatabase.TABLE_DEVICES.equals(intent.getStringExtra(AccessDatabase.EXTRA_TABLE_NAME))
			))
				refreshList();
			else if (getUIConnectionUtils().notifyWirelessRequestHandled()
					&& WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())
					&& WifiManager.WIFI_STATE_ENABLED == intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1)) {
				requestRefresh();
			}
		}
	}
}
