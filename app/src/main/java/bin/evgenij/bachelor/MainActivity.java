package bin.evgenij.bachelor;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;


//import static android.net.wifi.p2p.WifiP2pManager.EXTRA_WIFI_P2P_DEVICE;
import static android.net.wifi.p2p.WifiP2pManager.P2P_UNSUPPORTED;

/**
 * The main Activity where all the GUI elements are initialized and altered if needed. Implements the code
 * for WiFi and Bluetooth device discovery, establishing and managing a connection between devices.
 */
public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_ACCESS_LOCATION = 77;
    private static  final int REQUEST_WRITE_EXTERNAL = 88;
    Button discoverWifi, discoverBT, starttestBT, disconnectBtn, setupBT, cancel, startServerBT, starttestWifi, startServerWifi, cancelWifi;
    ListView peerList, peerListBT, clientListBT;
    TextView connecttime, result, connectedWifiDevice;
    EditText filesize, iterations;
    //boolean pairingInitiator=false;
    //long pairingTime=0;
    private boolean wifiDiscoveryRunning = false;
    private int mIterations;
    private boolean sendingOverWifi = false;
    private boolean sendingOverBluetooth = false;

    final ArrayList<WifiP2pDevice> devices = new ArrayList<>();
    final ArrayList<BluetoothDevice> devicesBT = new ArrayList<>();
    final ArrayList<BluetoothDevice> pairedDevicesBT = new ArrayList<>();
    final ArrayList<String> deviceNamesBT = new ArrayList<>();
    final ArrayList<String> deviceNamesWifi = new ArrayList<>();

    WifiP2pManager mManager;
    WifiP2pManager.Channel mChannel;


    BluetoothAdapter mBluetoothAdapter;
    ArrayAdapter<String> adapter, wifiAdapter;
    static final int REQUEST_ENABLE_DISCOVERABLE = 99;

    IntentFilter mIntentFilter,bFilter,stopwatchFilter;

    ConnectivityManager conManager;

    WiFiDirectService mWifiService;
    BluetoothService mBluetoothService;
    private static final UUID mUUID = UUID.fromString("822fac4e-9411-49e0-bc9e-44817cb9f5b6");
    BluetoothDevice device;

    InetAddress groupOwnerAddress;

    StopwatchService wifiDiscovery,btDiscovery/*,btConnect*/;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
        setupListeners();
        //showPopup();
    }

    /**
     * Initializes all GUI elements, variables, registers broadcastreceivers and
     * creates instances of Service classes which are needed for data transfer and elapsed time measurements.
     */
    public void init() {
        discoverWifi = findViewById(R.id.discoverWifi);
        discoverBT = findViewById(R.id.discoverBT);
        peerList = findViewById(R.id.peerList);
        peerListBT = findViewById(R.id.peerListBT);
        connectedWifiDevice = findViewById(R.id.connectedWifiDevice);
        clientListBT = findViewById(R.id.clientsBT);
        connecttime = findViewById(R.id.connecttime);
        result = findViewById(R.id.result);
        filesize = findViewById(R.id.filesize);
        iterations = findViewById(R.id.iterations);
        disconnectBtn = findViewById(R.id.disconnectBtn);
        disconnectBtn.setVisibility(View.INVISIBLE);
        setupBT = findViewById(R.id.setupBT);
        cancel = findViewById(R.id.cancel);
        cancel.setVisibility(View.INVISIBLE);
        starttestBT = findViewById(R.id.starttest);
        startServerBT = findViewById(R.id.startServerBT);
        starttestWifi = findViewById(R.id.sendWifi);
        startServerWifi = findViewById(R.id.acceptConnectionWifi);
        startServerWifi.setVisibility(View.INVISIBLE);
        cancelWifi = findViewById(R.id.cancelWifi);
        cancelWifi.setVisibility(View.INVISIBLE);
        wifiDiscovery = new StopwatchService();
        btDiscovery = new StopwatchService();
        //btConnect = new StopwatchService();

        /*Initialize Wifi P2P*/
        mManager = (WifiP2pManager)getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);

        /*Initialize Bluetooth*/
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        getPairedDevicesBT();
        bFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        bFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        bFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        bFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        bFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        bFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);

        stopwatchFilter = new IntentFilter();
        stopwatchFilter.addAction("clientStopwatchStopped");
        stopwatchFilter.addAction("serverStopwatchStopped");

        registerReceiver(bReceiver, bFilter);
        registerReceiver(WiFiDirectReceiver, mIntentFilter);
        registerReceiver(btServerStartedReceiver, new IntentFilter("bluetoothServerStarted"));
        registerReceiver(wifiServerStartedReceiver, new IntentFilter("wifiServerStarted"));
        registerReceiver(stopWatchReceiver,stopwatchFilter);

        conManager = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        adapter = new ArrayAdapter<>(getApplicationContext(),android.R.layout.simple_list_item_1,deviceNamesBT);
        peerListBT.setAdapter(adapter);
        wifiAdapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_list_item_1,deviceNamesWifi);
        peerList.setAdapter(wifiAdapter);
        mBluetoothService = new BluetoothService(MainActivity.this);
        mWifiService = new WiFiDirectService(MainActivity.this);

        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL);
    }
    /**
     * Initializes all necessary onClick-listeners.
     */
    public void setupListeners() {
        /**
         * Sets an onClick-Listener on the "WIFI" Button, which when tapped starts the WiFi Direct device discovery.
         */
        discoverWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager.isWifiEnabled()){
                    if(ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED) {
                        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                deviceNamesWifi.clear();
                                devices.clear();
                                wifiAdapter.notifyDataSetChanged();
                                wifiDiscovery.start();
                            }
                            @Override
                            public void onFailure(int i) {
                                if(i == P2P_UNSUPPORTED) {
                                    Toast.makeText(getApplicationContext(), "Your device does not support WiFi Direct", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(getApplicationContext(), "Peer Discovery Starting Failed ", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    } else {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_ACCESS_LOCATION);
                    }
                } else {
                    wifiManager.setWifiEnabled(true);
                    Toast.makeText(getApplicationContext(), "Wifi is on. You can scan for devices now.", Toast.LENGTH_SHORT).show();
                }
            }
        });
        /**
         * Sets an onClick-Listener on the "SETUP BT" Button, which when tapped calls the setupBT() function.
         */
        setupBT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setupBT();
            }
        });
        /**
         * Sets an onClick-Listener on the "BT" Button, which when tapped starts the Bluetooth device discovery.
         */
        discoverBT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED) {
                    if (mBluetoothAdapter.isDiscovering()) {
                        mBluetoothAdapter.cancelDiscovery();
                    }
                    if(mBluetoothAdapter.startDiscovery()) {
                        //start the "timer" for device discovery
                        btDiscovery.start();
                        if(devicesBT.size() > 0 || deviceNamesBT.size() > 0) {
                            devicesBT.clear();
                            deviceNamesBT.clear();
                        }
                        Toast.makeText(MainActivity.this, "Bluetooth discovery started.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Bluetooth discovery failed. Check if BT enabled.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_ACCESS_LOCATION);
                }
            }
        });
        /**
         * Sets an onClick-Listener on the "CANCEL" Button, which when tapped cancels the Bluetooth device discovery.
         */
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mBluetoothAdapter.cancelDiscovery();
            }
        });
        /**
         * Sets an onClick-Listener on the "DISCONNECT WIFI" Button, which when tapped calls the disconnectWifi() function.
         */
        disconnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                disconnectWifi();
            }
        });
        /**
         * Sets an onClick-Listener on the "Wifi Peers" ListView. When a device on the list is tapped
         * a connection request is sent to that device. The user gets notified by a Toast if the request was successful or not.
         * The connection request receiving device will get a dialog where he can accept or cancel the connection request.
         * Ongoing peer discovery is cancelled.
         */
        peerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                final WifiP2pDevice device = devices.get(i);
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;

                mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        if(wifiDiscoveryRunning) {
                            mManager.stopPeerDiscovery(mChannel,null);
                        }
                        Toast.makeText(MainActivity.this, "Connection Request sent to " + device.deviceName, Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onFailure(int i) {
                        Toast.makeText(MainActivity.this, "Connection Request Failed", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        /**
         * Sets an onClick-Listener on the "BT Peers" ListView. When a device on the list is tapped
         * a pairing request is sent to that device. A dialog is shown where the user can accept or cancel the pairing.
         */
        peerListBT.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                final BluetoothDevice device = devicesBT.get(i);
                if(mBluetoothAdapter.isDiscovering()) {
                    mBluetoothAdapter.cancelDiscovery();
                }
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    if(!pairedDevicesBT.contains(device)) {
                        device.createBond();
                        //pairingInitiator = true;
                    } else {
                        Toast.makeText(MainActivity.this, ""+device.getName() + " is already paired." , Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        /**
         * Sets an onClick-Listener on the "Paired" ListView. The MAC address of the tapped device is stored in
         * a variable for later usage (Establishing an RFCOMM socket connection).
         */
        clientListBT.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                device = pairedDevicesBT.get(i);
                Toast.makeText(MainActivity.this, ""+device, Toast.LENGTH_SHORT).show();
            }
        });
        /**
         * Sets an onClick-Listener on the "SEND BT" Button. Checks if the data size to send and the number
         * of iterations is valid and starts a client thread, which then sends data to the server device via Bluetooth.
         */
        starttestBT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(device != null) {
                    String tempBytes = filesize.getText().toString();
                    String tempIterations = iterations.getText().toString();
                    if(!tempBytes.isEmpty() && !tempIterations.isEmpty()) {
                        double mBytes = Double.parseDouble(tempBytes);
                        mIterations = Integer.parseInt(tempIterations);
                        if(mBytes > 0 && mIterations >= 1) {
                            clearFields();
                            mBluetoothService.startClient(device,mUUID, mBytes, mIterations);
                            sendingOverWifi = false;
                            sendingOverBluetooth = true;
                        }else {
                            Toast.makeText(MainActivity.this, "Data size must be > 0 and iterations must be an integer >= 1" , Toast.LENGTH_SHORT).show();
                        }
                    }
                } else {
                    Toast.makeText(MainActivity.this, "You must choose a device from list with paired devices" , Toast.LENGTH_SHORT).show();
                }
            }
        });
        /**
         * Sets an onClick-Listener on the "SEND WIFI" Button. Checks if the data size to send and the number
         * of iterations is valid and starts a client thread, which then sends data to the server device via WiFi Direct.
         */
        starttestWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String tempBytes = filesize.getText().toString();
                String tempIterations = iterations.getText().toString();
                if(!tempBytes.isEmpty() && !tempIterations.isEmpty()) {
                    double mBytes = Double.parseDouble(tempBytes);
                    mIterations = Integer.parseInt(tempIterations);
                    if(mBytes > 0 && mIterations >= 1) {
                        clearFields();
                        mWifiService.startClient(groupOwnerAddress, mBytes, mIterations);
                        sendingOverBluetooth = false;
                        sendingOverWifi = true;
                    }else {
                        Toast.makeText(MainActivity.this, "Data size must be > 0 and iterations must be an integer >= 1" , Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        /**
         * Sets an onClick-Listener on the "Accept Connection" Button.
         * When the button is tapped the server thread gets started to receive data via Bluetooth.
         */
        startServerBT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mBluetoothAdapter.isEnabled()) {
                    mBluetoothService.startServer();
                } else {
                    Toast.makeText(MainActivity.this, "You need to turn on Bluetooth first." , Toast.LENGTH_SHORT).show();
                }
            }
        });
        /**
         * Sets an onClick-Listener on the "Accept Connection" Button.
         * When the button is tapped the server thread gets started to receive data via WiFi Direct.
         */
        startServerWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mWifiService.startServer();
            }
        });
        /**
         * Sets an onClick-Listener on the "CANCEL" Button next to "WIFI"("DISCOVERING..." while peer discovery is running) Button.
         * When the button is tapped the Wi-Fi peer discovery is stopped.
         */
        cancelWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(wifiDiscoveryRunning) {
                    mManager.stopPeerDiscovery(mChannel,null);
                }
            }
        });

    }

    /**
     * A callback function which gets called when WiFi Direct devices are discovered.
     * Fills a list with nearby discovered WiFi Direct devices and shows them in the "Wfi Peers" List
     * together with the number of seconds it took to discover each device.
     */
    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList deviceList) {
            for(WifiP2pDevice d : deviceList.getDeviceList()) {
                if(!devices.contains(d)) {
                    deviceNamesWifi.add(d.deviceName+"("+wifiDiscovery.getResultMillis()/1000.0+"s"+")");
                    devices.add(d);
                    wifiAdapter.notifyDataSetChanged();
                }
            }
        }
    };
    /**
     * A callback function which gets called when a connection between WiFi Direct devices is established.
     * If a group is formed and a device is the group owner, the "Accept Connection" Button is shown to start the server thread.
     * Else, if a device is not the group owner, the address of the group owner is saved in a variable.
     */
    WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
            disconnectBtn.setVisibility(View.VISIBLE);
            if(wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
                startServerWifi.setVisibility(View.VISIBLE);
            } else if(wifiP2pInfo.groupFormed) {
                groupOwnerAddress = wifiP2pInfo.groupOwnerAddress;
            }
            deviceNamesWifi.clear();
            devices.clear();
            wifiAdapter.notifyDataSetChanged();
        }
    };
    /**
     * A callback function which gets called when a connection between WiFi Direct devices is established and a group was formed.
     * Shows text which notifies the user if his device is the group owner or not.
     */
    WifiP2pManager.GroupInfoListener groupInfoListener = new WifiP2pManager.GroupInfoListener() {
        @Override
        public void onGroupInfoAvailable(WifiP2pGroup wifiP2pGroup) {
            if (wifiP2pGroup.isGroupOwner()) {
                connectedWifiDevice.setText("I am the Group Owner. Ready to receive data.");
            } else {
                connectedWifiDevice.setText("Group Owner: "+wifiP2pGroup.getOwner().deviceName);
            }
        }
    };
    /**
     * Gets called when the user responded to a dialog, which asks the user to allow access to his location.
     * Notifies the user whether the permission to access his location was given or not. If yes, the user
     * can start scanning for WiFi Direct or Bluetooth devices.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == REQUEST_ACCESS_LOCATION) {
            if (permissions.length == 1 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(MainActivity.this,
                        "Permission accepted. You can scan for devices now.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(MainActivity.this,
                        "Permission denied.", Toast.LENGTH_LONG).show();
            }
        }/* else if(requestCode == REQUEST_WRITE_EXTERNAL) {
            if (permissions.length == 1 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {

            }
        }*/
    }

    /**
     * Removes the WiFi Direct group which the calling device is connected to.
     */
    public void disconnectWifi() {
        mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(getApplicationContext(), "Wifi group removed", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onFailure(int reason) {
                Toast.makeText(getApplicationContext(), "Wifi group removal failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Check if the device supports Bluetooth. If yes, a dialog is shown to the user to allow making
     * the device discoverable to other Bluetooth devices for 60 seconds. Automatically enables Bluetooth
     * if it was disabled.
     */
    public void setupBT() {
        if(mBluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "This device doesn't support Bluetooth.", Toast.LENGTH_SHORT).show();
        } else {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 60);
            startActivityForResult(discoverableIntent, REQUEST_ENABLE_DISCOVERABLE);
        }
    }

    /**
     * Called after the user accepted or declined the request to make the device discoverable for other Bluetooth devices.
     * If the request was accepted the user gets notified that he can start discovering other Bluetooth devices.
     * @param requestCode A code the activity was started with.
     * @param resultCode The result of the started activity.
     * @param data The intent of the started activity.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_ENABLE_DISCOVERABLE) {
            if(resultCode == RESULT_CANCELED) {
                Toast.makeText(MainActivity.this, "Other Bluetooth devices won't be able to find your device.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Bluetooth is enabled. You can start searching for devices.", Toast.LENGTH_SHORT).show();
            }
        }
    }
    /**
     * Calls a function which stops the WiFi Direct server thread.
     */
    public void stopWifiServer() {
        mWifiService.stopServer();
    }
    /**
     * Gets a list of paired Bluetooth devices and shows it in the "Paired" ListView.
     */
    public void getPairedDevicesBT() {
        if(pairedDevicesBT.size() > 0) {
            pairedDevicesBT.clear();
        }
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            List<String> deviceNames = new ArrayList<>();
            for (BluetoothDevice device : pairedDevices) {
                deviceNames.add(device.getName());
                pairedDevicesBT.add(device);
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(),android.R.layout.simple_list_item_1,deviceNames);
            clientListBT.setAdapter(adapter);
        }
    }
    /**
     * Clears the GUI fields for elapsed time measurements.
     */
    public void clearFields() {
        connecttime.setText("");
        result.setText("");
    }

    /**
     * Writes the measured times for data transfer to a file.
     * @param measurements The measured times
     */
    public void writeToFile(String measurements)
    {
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date = new Date();
        String transTech = "";
        if(sendingOverBluetooth) {
            transTech = "Bluetooth";
        } else if(sendingOverWifi) {
            transTech = "WifiDirect";
        }
        String measureTime = dateFormat.format(date);
        String path = Environment.getExternalStorageDirectory()+"/"+"measurements/";
        File dir = new File(path);
        if(!dir.exists()) {
            dir.mkdirs();
        }
        String fileName = path+transTech+"measurements"+".txt";
        File file = new File(fileName);
        try {
            if (!file.exists())
            {
                file.createNewFile();
            }
            BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));
            bw.append(measureTime+" >> "+measurements);
            bw.newLine();
            bw.close();
        }
        catch (IOException e)
        {
            Log.e("MainActivity", e.getMessage());
        }
    }
    /**
     * A Broadcast receiver to listen to Bluetooth related system events (e.g. device discovery, bluetooth adapter state)
     */
    private final BroadcastReceiver bReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(!pairedDevicesBT.contains(device)) {
                    if(device.getName() != null) {
                        btDiscovery.stop();
                        deviceNamesBT.add(device.getName()+"("+btDiscovery.getResultMillis()/1000.0+"s"+")");
                        adapter.notifyDataSetChanged();
                        devicesBT.add(device);
                    }
                } else {
                    //Toast.makeText(MainActivity.this, "This device " + device.getName() + " is already paired", Toast.LENGTH_SHORT).show();
                }
            } else if(BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                setupBT.setVisibility(View.INVISIBLE);
                cancel.setVisibility(View.VISIBLE);
            } else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setupBT.setVisibility(View.VISIBLE);
                cancel.setVisibility(View.INVISIBLE);
                Toast.makeText(MainActivity.this, "Bluetooth Discovery canceled", Toast.LENGTH_SHORT).show();
            } else if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                final int newState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                final int oldState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);
                if(oldState == BluetoothDevice.BOND_NONE && newState == BluetoothDevice.BOND_BONDING) {
                    /*if(pairingInitiator) {
                        btConnect.start();
                    }*/
                } else if(oldState == BluetoothDevice.BOND_BONDING && newState == BluetoothDevice.BOND_BONDED) {
                    getPairedDevicesBT();
                } else if(oldState == BluetoothDevice.BOND_BONDED && newState == BluetoothDevice.BOND_NONE) {
                    getPairedDevicesBT();
                }
            } else if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                if(state == BluetoothAdapter.STATE_ON) {
                    getPairedDevicesBT();
                }
            }/* else if(BluetoothDevice.ACTION_PAIRING_REQUEST.equals(action)) {
                //Toast.makeText(MainActivity.this, "ACTION PAIRING REQUEST", Toast.LENGTH_SHORT).show();
                if(pairingInitiator) {
                    btConnect.stop();
                    pairingTime = btConnect.getResultMillis();
                    Toast.makeText(MainActivity.this, "Pairing Initiation Time: "+pairingTime/1000.0, Toast.LENGTH_SHORT).show();
                    pairingInitiator = false;
                }
            }*/
        }
    };
    /**
     * A Broadcast receiver to listen to WiFi Direct related system events (e.g. device discovery, WiFi adapter state, connection change events)
     */
    private final BroadcastReceiver WiFiDirectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(mManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    //Toast.makeText(context, "Wifi P2P is on", Toast.LENGTH_SHORT).show();
                } else {
                    //Toast.makeText(context, "Wifi P2P is off", Toast.LENGTH_SHORT).show();
                }
            } else if (mManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                if(mManager!=null) {
                    wifiDiscovery.stop();
                    mManager.requestPeers(mChannel,peerListListener);
                }
            } else if (mManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                if(mManager!=null) {
                    NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                    if(networkInfo.isConnected()) {
                        mManager.requestConnectionInfo(mChannel, connectionInfoListener);
                        mManager.requestGroupInfo(mChannel, groupInfoListener);
                        Toast.makeText(context, "P2P connection active", Toast.LENGTH_SHORT).show();
                        //Stop discovering peers once connected
                        if(wifiDiscoveryRunning) {
                            mManager.stopPeerDiscovery(mChannel,null);
                        }
                    } else {
                        connectedWifiDevice.setText("");
                        disconnectBtn.setVisibility(View.INVISIBLE);
                        startServerWifi.setVisibility(View.INVISIBLE);
                        stopWifiServer();
                    }
                }
            } else if(mManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {
                int discovery = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, 100);
                if(discovery == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                    Toast.makeText(context, "WIFI P2P Discovery Started.", Toast.LENGTH_SHORT).show();
                    discoverWifi.setText("Discovering...");
                    cancelWifi.setVisibility(View.VISIBLE);
                    wifiDiscoveryRunning = true;
                } else if(discovery == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) {
                    //Toast.makeText(context, "WIFI P2P Discovery not running.", Toast.LENGTH_SHORT).show();
                    discoverWifi.setText("WIFI");
                    cancelWifi.setVisibility(View.INVISIBLE);
                    wifiDiscoveryRunning = false;
                }
            }/* else if(mManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                WifiP2pDevice dev = intent.getParcelableExtra(EXTRA_WIFI_P2P_DEVICE);
                Toast.makeText(context, "device status: " + dev.status, Toast.LENGTH_SHORT).show();
            }*/
        }
    };
    /**
     * A Broadcast receiver that receives the "bluetoothServerStarted" intent, which contains information about
     * whether the server thread for Bluetooth data transfer has started or stopped. GUI elements change according
     * to the received intent's extra.
     */
    private final BroadcastReceiver btServerStartedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals("bluetoothServerStarted")) {
                String text = intent.getStringExtra("status");
                if(text.equals("started")) {
                    Toast.makeText(MainActivity.this, "Server started.", Toast.LENGTH_SHORT).show();
                    startServerBT.setBackgroundColor(Color.parseColor("#ff99cc00"));
                    startServerBT.setText("Accepting...");
                } else if(text.equals("stopped")) {
                    startServerBT.setBackgroundColor(Color.parseColor("#FFFF4444"));
                    startServerBT.setText("Accept Connection");
                }
            }
        }
    };
    /**
     * A Broadcast receiver that receives the "clientStopwatchStopped" and "serverStopwatchStopped" intents,
     * which contain the time it took to send/receive and to establish a socket connection. The times
     * are shown on the GUI.
     */
    private final BroadcastReceiver stopWatchReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            clearFields();
            if(action.equals("clientStopwatchStopped")) {
                long sum = 0;
                long socketConnTime = intent.getLongExtra("SOCKET_TIME",0);
                long times[] = intent.getLongArrayExtra("times");
                for(long l : times) {
                    sum += l;
                }
                String conntime = "Socket Connection established in: "+socketConnTime/1000.0+" seconds";
                String resulttime = "";
                if(times.length == 1) {
                    resulttime = "Data sending duration: "+sum/1000.0+" seconds";
                    writeToFile(String.valueOf(sum/1000.0));
                } else if(times.length > 1) {
                    double[] arr = new double[times.length];
                    for(int i = 0; i < times.length; i++) {
                        arr[i] = times[i]/1000.0;
                    }
                    double averageSeconds = (sum/times.length)/1000.0;
                    resulttime = "Data sending duration: "+sum/1000.0+" seconds\nRound times (seconds): "+ Arrays.toString(arr)+"\n"+"Average (seconds): "+averageSeconds;
                    if(ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                            PackageManager.PERMISSION_GRANTED) {
                        writeToFile(Arrays.toString(arr) + "\n" + "Average: " + averageSeconds);
                    }
                }
                connecttime.setText(conntime);
                result.setText(resulttime);
            } else if(action.equals("serverStopwatchStopped")) {
                long receivingTime = intent.getLongExtra("time",0);
                result.setText("Data receiving duration: "+receivingTime/1000.0+" seconds");
            }
        }
    };
    /**
     * A Broadcast receiver that receives the "wifiServerStarted" intent, which contains information about
     * whether the server thread for WiFi Direct data transfer has started or stopped. GUI elements change according
     * to the received intent's extra.
     */
    private final BroadcastReceiver wifiServerStartedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals("wifiServerStarted")) {
                String text = intent.getStringExtra("status");
                if(text.equals("started")) {
                    Toast.makeText(MainActivity.this, "Wifi Server started.", Toast.LENGTH_SHORT).show();
                    startServerWifi.setBackgroundColor(Color.parseColor("#ff99cc00"));
                    startServerWifi.setText("Accepting...");
                } else if(text.equals("stopped")) {
                    startServerWifi.setBackgroundColor(Color.parseColor("#FFFF4444"));
                    startServerWifi.setText("Accept Connection");
                }
            }
        }
    };

    /**
     * Cancels Bluetooth discovery and unregisters broadcastreceivers when the action is destroyed.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        if(wifiDiscoveryRunning) {
            mManager.stopPeerDiscovery(mChannel,null);
        }
        unregisterReceiver(bReceiver);
        unregisterReceiver(WiFiDirectReceiver);
        unregisterReceiver(btServerStartedReceiver);
        unregisterReceiver(stopWatchReceiver);
        unregisterReceiver(wifiServerStartedReceiver);
    }
}
