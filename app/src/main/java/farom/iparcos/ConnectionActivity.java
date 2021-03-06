package farom.iparcos;

import farom.iparcos.R;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import laazotea.indi.client.INDIDevice;
import laazotea.indi.client.INDIDeviceListener;
import laazotea.indi.client.INDIProperty;
import laazotea.indi.client.INDIServerConnection;
import laazotea.indi.client.INDIServerConnectionListener;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Button;
import android.preference.PreferenceManager;

/**
 * The main activity of the application. It manages the connection.
 * 
 * @author Romain Fafet
 * 
 */
public class ConnectionActivity extends Activity implements INDIServerConnectionListener, INDIDeviceListener {

	private static INDIServerConnection connection;
	private static ConnectionActivity instance = null;

	// a list to re-add the listener when the connection is destroyed and
	// recreated
	private ArrayList<INDIServerConnectionListener> permanentConnectionListeners;

	// views
	private TextView logView;
	private Button connectionButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		instance = this;
		permanentConnectionListeners = new ArrayList<INDIServerConnectionListener>();

		setContentView(R.layout.activity_connection);

		loadServerList();

		logView = (TextView) findViewById(R.id.logTextBox); // TODO : correct
															// scroll bug
		logView.setMovementMethod(new ScrollingMovementMethod());

		connectionButton = ((Button) findViewById(R.id.connectionButton));

		((Spinner) findViewById(R.id.spinnerHost)).setOnItemSelectedListener(new OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
				if (parent.getItemAtPosition(pos).toString().equals(getResources().getString(R.string.hostadd))) {
					addServer();
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				// nothing to do
			}
		});

	}

	/**
	 * load and update the server list
	 */
	protected void loadServerList() {
		// get preferences
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		Set<String> set = preferences.getStringSet("SERVER_SET", null);
		List<String> serverList;
		if (set != null) {
			serverList = new Vector<String>(set);
		} else {
			serverList = new Vector<String>();
		}

		// update the display
		serverList.add(getResources().getString(R.string.hostadd));
		ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
				serverList);
		dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		((Spinner) findViewById(R.id.spinnerHost)).setAdapter(dataAdapter);
	}

	/**
	 * Called when the connect/disconnect button is clicked
	 * 
	 * @param v
	 */
	public void connectionButtonClicked(View v) {
		// Retrieve Hostname and port number
		String host = String.valueOf(((Spinner) findViewById(R.id.spinnerHost)).getSelectedItem());
		String portStr = ((EditText) findViewById(R.id.editTextPort)).getText().toString();
		int port;
		try {
			port = Integer.parseInt(portStr);
		} catch (NumberFormatException e) {
			port = 7624;
		}

		// Connect (or disconnect)
		if (connectionButton.getText().equals(getResources().getString(R.string.connect))) {
			if (host.equals(getResources().getString(R.string.hostadd))) {
				addServer();
			} else {
				connect(host, port);
			}
		} else if (connectionButton.getText().equals(getResources().getString(R.string.disconnect))) {
			disconnect();
		}

	}

	/**
	 * Add the server address, save the server list and update the spinner
	 * 
	 * @param ip
	 */
	protected void addServer(String ip) {
		// Retrieve the list
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		Set<String> set = preferences.getStringSet("SERVER_SET", null);
		List<String> serverList;
		if (set != null) {
			serverList = new Vector<String>(set);
		} else {
			serverList = new Vector<String>();
		}
		serverList.add(0, ip);

		// Save the list
		Set<String> newSet = new HashSet<String>();
		newSet.addAll(serverList);
		SharedPreferences.Editor editor = preferences.edit();
		editor.putStringSet("SERVER_SET", newSet);
		editor.commit();

		// Update
		loadServerList();
	}

	/**
	 * Ask to the user to add a new server
	 */
	@SuppressLint("InflateParams")
	protected void addServer() {
		// get prompts.xml view
		LayoutInflater li = LayoutInflater.from(this);
		View promptsView = li.inflate(R.layout.ip_prompt, null);

		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

		alertDialogBuilder.setView(promptsView);

		final EditText userInput = (EditText) promptsView.findViewById(R.id.editTextDialogUserInput);

		// set dialog message
		alertDialogBuilder.setCancelable(false)
				.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						addServer(userInput.getText().toString());
					}
				}).setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});

		// create alert dialog
		AlertDialog alertDialog = alertDialogBuilder.create();

		// show it
		alertDialog.show();

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.global, menu);

       	// hide the item for the current activity
		MenuItem connectionItem = menu.findItem(R.id.menu_connection);
		connectionItem.setVisible(false);

		if (connection == null || !connection.isConnected()) {
			// hide the items which are not available when disconnected
			MenuItem genericItem = menu.findItem(R.id.menu_generic);
			genericItem.setVisible(false);
			MenuItem moveItem = menu.findItem(R.id.menu_move);
			moveItem.setVisible(false);
			
		}
		
		ActionBar ab = getActionBar();
		ab.setSubtitle(R.string.menu_connection);

		return true;
	}

	/**
	 * open the motion activity,
	 * 
	 * @param v
	 */
	public boolean openMotionActivity(MenuItem v) {
		Intent intent = new Intent(this, MotionActivity.class);
		startActivity(intent);
		return true;
	}

	/**
	 * open the settings activity
	 * 
	 * @param v
	 * @return
	 */
	public boolean openSettingsActivity(MenuItem v) {
		// TODO Add settings
		return false;
	}

	/**
	 * open the generic activity
	 * 
	 * @param v
	 * @return
	 */
	public boolean openGenericActivity(MenuItem v) {
		Intent intent = new Intent(this, GenericActivity.class);
		startActivity(intent);
		return true;
	}

    /**
     * open the search activity
     *
     * @param v
     * @return
     */
    public boolean openSearchActivity(MenuItem v) {
        Intent intent = new Intent(this, SearchActivity.class);
        startActivity(intent);
        return true;
    }

	/**
	 * open the connection activity
	 * 
	 * @param v
	 * @return
	 */
	public boolean openConnectionActivity(MenuItem v) {
		// nothing to do, already the current activity
		return false;
	}

	/**
	 * Connect to the driver
	 * 
	 * @param host
	 * @param port
	 */
	private void connect(java.lang.String host, int port) {
		connectionButton.setText(R.string.connecting);
		appendLog(getString(R.string.try_to_connect) + host + ":" + port);
		connection = new INDIServerConnection(host, port);

		connection.addINDIServerConnectionListener(this); // We listen to all
		for (Iterator<INDIServerConnectionListener> it = permanentConnectionListeners.iterator(); it.hasNext();) {
			connection.addINDIServerConnectionListener(it.next());
		}

		new Thread(new Runnable() {
			public void run() {
				try {
					connection.connect();
					connection.askForDevices(); // Ask for all the devices.
					appendLog(getString(R.string.connected));
					connectionButton.post(new Runnable() {
						public void run() {
							connectionButton.setText(R.string.disconnect);
							invalidateOptionsMenu();
						}
					});
				} catch (IOException e) {
					appendLog(getString(R.string.connection_pb));
					appendLog(e.getLocalizedMessage());
					connectionButton.post(new Runnable() {
						public void run() {
							connectionButton.setText(R.string.connect);
						}
					});
				}

			}
		}).start();
	}

	/**
	 * Breaks the connection
	 */
	public void disconnect() {
		connection.disconnect();
	}

	@Override
	public void newDevice(INDIServerConnection connection, INDIDevice device) {
		device.addINDIDeviceListener(this);
		appendLog(getString(R.string.new_device) + device.getName());
	}

	@Override
	public void removeDevice(INDIServerConnection connection, INDIDevice device) {
		device.removeINDIDeviceListener(this);
		appendLog(getString(R.string.device_removed) + device.getName());
	}

	@Override
	public void connectionLost(INDIServerConnection connection) {
		appendLog(getString(R.string.connection_lost));
		connectionButton.post(new Runnable() {
			public void run() {
				connectionButton.setText(R.string.connect);
				invalidateOptionsMenu();
			}
		});

		// Open the connection activity
		Intent intent = new Intent(this, ConnectionActivity.class);
		startActivity(intent);
	}

	/**
	 * Display the message in the log view
	 * 
	 * @param message
	 */
	public void appendLog(String message) {
		final String msg = message + "\n";
		Log.i("GLOBALLOG", message);
		logView.post(new Runnable() {
			public void run() {
				logView.append(msg);
			}
		});
	}

	@Override
	public void newMessage(INDIServerConnection connection, Date timestamp, String message) {
		appendLog(message);
	}

	/**
	 * Add a INDIServerConnectionListener to the connection. If the connection
	 * is re-created, the listener will be re-installed
	 * 
	 * @param arg
	 *            the listener
	 */
	public void registerPermanentConnectionListener(INDIServerConnectionListener arg) {
		permanentConnectionListeners.add(arg);
		if (connection != null) {
			connection.addINDIServerConnectionListener(arg);
		}
	}

	/**
	 * remove the listener
	 * 
	 * @param arg
	 *            the listener
	 */
	public void unRegisterPermanentConnectionListener(INDIServerConnectionListener arg) {
		permanentConnectionListeners.remove(arg);
		if (connection != null) {
			connection.removeINDIServerConnectionListener(arg);
		}
	}

	/**
	 * @return the connection, it may be null if the connection doesnot exist
	 */
	public static INDIServerConnection getConnection() {
		return connection;
	}

	/**
	 * @return the instance of the activity
	 */
	public static ConnectionActivity getInstance() {
		return instance;
	}

	@Override
	public void newProperty(INDIDevice device, INDIProperty property) {
		// nothing

	}

	@Override
	public void removeProperty(INDIDevice device, INDIProperty property) {
		// nothing

	}

	@Override
	public void messageChanged(INDIDevice device) {
		appendLog(device.getName() + ": " + device.getLastMessage());
	}

}
