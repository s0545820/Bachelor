package bin.evgenij.bachelor;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.UUID;
/**
 * This class provides functionality for starting the server and the client threads and sending/receiving
 * data using Bluetooth.
 */
public class BluetoothService {
    private static final String mApp = "app";

    private static final UUID mUUID = UUID.fromString("822fac4e-9411-49e0-bc9e-44817cb9f5b6");

    private final BluetoothAdapter mBluetoothAdapter;
    private Context mContext;
    private Activity act;

    private AcceptThread mAcceptThread;

    private ConnectThread mConnectThread;
    private BluetoothDevice mmDevice;
    private UUID deviceUUID;
    private ProgressDialog rpd;



    public BluetoothService(Context context) {
        mContext = context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        act = (Activity) mContext;
    }

    /**
     * The server thread which listens for incoming rfcomm socket connections and receives
     * data from the client.
     */
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;
        private InputStream mInStream;
        private DataInputStream dInStream;
        private long mDataSize;
        private StopwatchService sws;

        public AcceptThread(){
            BluetoothServerSocket tmp = null;
            try{
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(mApp, mUUID);
            }catch (IOException e){
                e.printStackTrace();
            }
            mmServerSocket = tmp;
        }
        public void run(){
            BluetoothSocket socket = null;
            try{
                Intent intent = new Intent("bluetoothServerStarted");
                intent.putExtra("status", "started");
                mContext.sendBroadcast(intent);
                if(mmServerSocket != null) {
                    socket = mmServerSocket.accept();
                }
                mInStream = socket.getInputStream();
                dInStream = new DataInputStream(socket.getInputStream());
                mDataSize = dInStream.readLong();
                byte[] buffer = new byte[2048];
                int bytes;
                int receivedBytes = 0;
                act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showReceivingProgress();
                    }
                });
                sws  = new StopwatchService();
                sws.start();
                while (receivedBytes != mDataSize) {
                    bytes = mInStream.read(buffer);
                    receivedBytes += bytes;
                    Log.d("BluetoothService", "InputStream: Received Bytes: " + receivedBytes);
                }
                sws.stop();
                Log.d("a", "InputStream: End of transmission, total bytes received: " + receivedBytes);

                Intent intentStopwatch = new Intent("serverStopwatchStopped");
                intentStopwatch.putExtra("time", sws.getResultMillis());
                mContext.sendBroadcast(intentStopwatch);
            }catch (IOException ioe){
                Log.e("BluetoothS-ServerThread", "IOException: "+ioe.getMessage());
            } catch (OutOfMemoryError om) {
                Log.e("BluetoothS-ServerThread", "OutOfMemoryError: "+om.getMessage());
            }
            act.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    rpd.dismiss();
                }
            });
            Intent intent = new Intent("bluetoothServerStarted");
            intent.putExtra("status", "stopped");
            mContext.sendBroadcast(intent);
        }
        /**
         * Closes the socket.
         */
        public void closeSock() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * The client thread which establishes a tcp socket connection to the server device and sends
     * data.
     */
    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket;
        private OutputStream mOutStream;
        private DataOutputStream dOutStream;
        private double mTotalDataSize;
        private int mIterations;
        private StopwatchService sws;
        private long socketConnectTime;

        public ConnectThread(BluetoothDevice device, UUID uuid, double datasize, int iters) {
            mmDevice = device;
            deviceUUID = uuid;
            mTotalDataSize = datasize;
            mIterations = iters;
        }

        public void run(){
            try {
                BluetoothSocket tmp = null;
                tmp = mmDevice.createRfcommSocketToServiceRecord(deviceUUID);
                mmSocket = tmp;
                if(mBluetoothAdapter.isDiscovering()) {
                    mBluetoothAdapter.cancelDiscovery();
                }
                sws = new StopwatchService();
                sws.start();
                mmSocket.connect();
                sws.stop();
                socketConnectTime = sws.getResultMillis();

                mOutStream = mmSocket.getOutputStream();
                dOutStream = new DataOutputStream(mmSocket.getOutputStream());

                double MBInBytes = mTotalDataSize*(1024*1024);
                final int MBinBytes = (int)MBInBytes;
                dOutStream.writeLong(MBinBytes*mIterations);
                Runtime runTime = Runtime.getRuntime();
                long maxMemory = runTime.maxMemory();

                sws.start();
                if(maxMemory >= mTotalDataSize) {
                    byte[] bytes = new byte[MBinBytes];
                    for(int i = 0; i < mIterations; i++) {
                        mOutStream.write(bytes);
                        mOutStream.flush();
                        sws.stopRound();
                    }
                } else {
                    for(int i = 0; i < mIterations; i++) {
                        long temp = MBinBytes;
                        while (temp >= 0) {
                            int size = (int) maxMemory;
                            byte[] bytes = new byte[size];
                            mOutStream.write(bytes);
                            mOutStream.flush();
                            sws.stopRound();
                            temp -= size;
                        }
                    }
                }
                Intent intentStopwatch = new Intent("clientStopwatchStopped");
                intentStopwatch.putExtra("SOCKET_TIME", socketConnectTime);
                intentStopwatch.putExtra("times", sws.getRoundTimesMillis());
                mContext.sendBroadcast(intentStopwatch);
            } catch (IOException ioe) {
                Log.e("BluetoothS-ClientThread", "IOException: "+ioe.getMessage());
            } catch (OutOfMemoryError om) {
                Log.e("BluetoothS-ClientThread", "OutOfMemoryError: "+om.getMessage());
            }
        }

        /**
         * Closes the socket.
         */
        public void closeSock() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Starts the server thread.
     */
    public synchronized void startServer() {
        if (mAcceptThread == null) {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        } else if(mAcceptThread != null && !mAcceptThread.isAlive()) {
            mAcceptThread.closeSock();
            mAcceptThread = null;
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }
    }

    /**
     * Starts the client thread.
     * @param device The device to which an rfcomm socket connection has to be established.
     * @param uuid A unique string to identify the application's Bluetooth service.
     * @param datasize The size of data that has to be transferred to the server device.
     * @param iters The number of times the data has to be sent.
     */
    public void startClient(BluetoothDevice device,UUID uuid, double datasize, int iters){
        if (mConnectThread == null) {
            mConnectThread = new ConnectThread(device, uuid, datasize, iters);
            mConnectThread.start();
        } else if(mConnectThread != null && !mConnectThread.isAlive()) {
            mConnectThread.closeSock();
            mConnectThread = null;
            mConnectThread = new ConnectThread(device, uuid, datasize, iters);
            mConnectThread.start();
        }
    }

    /**
     * Shows a Progress Dialog.
     */
    public void showReceivingProgress() {
        rpd = new ProgressDialog(mContext);
        rpd.setIndeterminate(true);
        rpd.setMessage("Receiving Data...");
        rpd.show();
    }
}

































