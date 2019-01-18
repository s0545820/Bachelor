package bin.evgenij.bachelor;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * This class provides functionality for starting the server and the client threads and sending/receiving
 * data using WiFi.
 */
public class WiFiDirectService {

    private int port = 1337;
    private Context mContext;

    private ServerThread serverThread;
    private ClientThread clientThread;

    private ProgressDialog rpd;

    private Activity act;


    public WiFiDirectService(Context context) {
        mContext = context;
        act = (Activity) mContext;
    }

    /**
     * The server thread which listens for incoming tcp socket connections and receives
     * data from the client.
     */
    private class ServerThread extends Thread {
        Socket mSocket;
        ServerSocket serverSocket;
        private DataInputStream dInStream;
        private long mDataSize;
        private InputStream mInStream;
        private StopwatchService sws;

        @Override
        public void run() {
            try {
                Intent intent = new Intent("wifiServerStarted");
                intent.putExtra("status", "started");
                mContext.sendBroadcast(intent);
                serverSocket = new ServerSocket(port);
                if(serverSocket != null) {
                    mSocket = serverSocket.accept();
                }
                //Receive Data
                mInStream = mSocket.getInputStream();
                dInStream = new DataInputStream(mSocket.getInputStream());

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
                }
                port++;
                Log.d("WifiServerThread", "InputStream: End of transmission, total bytes received: " + receivedBytes);
                sws.stop();
                Intent intentStopwatch = new Intent("serverStopwatchStopped");
                intentStopwatch.putExtra("time", sws.getResultMillis());
                mContext.sendBroadcast(intentStopwatch);
            } catch (IOException e) {
                e.getMessage();
            } catch (OutOfMemoryError om) {
                om.getMessage();
            }
            act.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    rpd.dismiss();
                }
            });
            Intent intent = new Intent("wifiServerStarted");
            intent.putExtra("status", "stopped");
            mContext.sendBroadcast(intent);
            Log.d("WiFiDirectService: ", "ServerThread ended.");
        }

        /**
         * Closes the socket.
         */
        public void close() {
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * The client thread which establishes a tcp socket connection to the server device and sends
     * data.
     */
    private class ClientThread extends Thread {
        Socket socket;
        String serverAddress;
        private OutputStream mOutStream;
        private DataOutputStream dOutStream;
        private double mTotalDataSize;
        private int mIterations;
        private StopwatchService sws;
        private long socketConnectTime;
        private byte[] bytes;

        public ClientThread(InetAddress address, double datasize, int iters) {
            serverAddress = address.getHostAddress();
            socket = new Socket();
            mTotalDataSize = datasize;
            mIterations = iters;
        }
        @Override
        public void run() {
            try {
                sws = new StopwatchService();
                sws.start();
                socket.connect(new InetSocketAddress(serverAddress,port),300);
                sws.stop();
                socketConnectTime = sws.getResultMillis();

                mOutStream = socket.getOutputStream();
                dOutStream = new DataOutputStream(socket.getOutputStream());

                double MBInBytes = mTotalDataSize*(1024*1024);
                int MBinBytes = (int)MBInBytes;
                dOutStream.writeLong(MBinBytes*mIterations);
                Runtime runTime = Runtime.getRuntime();
                long maxMemory = runTime.maxMemory();
                sws.start();
                if(maxMemory > mTotalDataSize) {
                    bytes = new byte[MBinBytes];
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
                            bytes = new byte[size];
                            mOutStream.write(bytes);
                            mOutStream.flush();
                            sws.stopRound();
                            temp -= size;
                        }
                    }
                }
                port++;
                Intent intentStopwatch = new Intent("clientStopwatchStopped");
                intentStopwatch.putExtra("SOCKET_TIME", socketConnectTime);
                intentStopwatch.putExtra("times", sws.getRoundTimesMillis());
                mContext.sendBroadcast(intentStopwatch);
            } catch (IOException ioe) {
                Log.e("WiFiDirectService", "IOException: "+ioe.getMessage());
            } catch (OutOfMemoryError om) {
                Log.e("WiFiDirectService", "OutOfMemoryError: "+om.getMessage());
            } catch (Exception e) {
                Log.e("WiFiDirectService", "Exception: "+e.getMessage());
            }
            Log.d("WiFiDirectService: ", "ClientThread ended.");
        }

        /**
         * Closes the socket.
         */
        public void close() {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Starts the server thread.
     */
    public synchronized void startServer() {
        if (serverThread == null) {
            serverThread = new ServerThread();
            serverThread.start();
        } else if(serverThread != null && !serverThread.isAlive()) {
            serverThread.close();
            serverThread = null;
            serverThread = new ServerThread();
            serverThread.start();
        }
    }

    /**
     * Starts the client thread.
     * @param groupOwnerAddress The address of the owner of the group the calling device wants to connect to.
     * @param datasize The size of data that has to be transferred to the server device.
     * @param iters The number of times the data has to be sent.
     */
    public void startClient(InetAddress groupOwnerAddress, double datasize, int iters){
        if (clientThread == null) {
            clientThread = new ClientThread(groupOwnerAddress, datasize, iters);
            clientThread.start();
        } else if(clientThread != null && !clientThread.isAlive()) {
            clientThread.close();
            clientThread = null;
            clientThread = new ClientThread(groupOwnerAddress, datasize, iters);
            clientThread.start();
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

    /**
     * Stops the server thread if it is running.
     */
    public void stopServer() {
        if(serverThread != null) {
            serverThread = null;
            Intent intent = new Intent("wifiServerStarted");
            intent.putExtra("status", "stopped");
            mContext.sendBroadcast(intent);
        }
    }

}
