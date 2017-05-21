package edu.buffalo.cse.cse486586.groupmessenger1;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    static final int SERVER_PORT = 10000;
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";

    private int counter;
    private Uri mUri;
    private ContentResolver cr;

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        counter = 0;
        mUri  = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger1.provider");
        cr = getContentResolver();

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));


        try {
            /*
             * Create a server socket as well as a thread (AsyncTask) that listens on the server
             * port.
             *
             * AsyncTask is a simplified thread construct that Android provides. Please make sure
             * you know how it works by reading
             * http://developer.android.com/reference/android/os/AsyncTask.html
             */
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            /*
             * Log is a good way to debug your code. LogCat prints out all the messages that
             * Log class writes.
             *
             * Please read http://developer.android.com/tools/debugging/debugging-projects.html
             * and http://developer.android.com/tools/debugging/debugging-log.html
             * for more information on debugging.
             */
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }


        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */

        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        EditText et = (EditText) findViewById(R.id.editText1);
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView tv = (TextView) findViewById(R.id.textView1);
                EditText et = (EditText) findViewById(R.id.editText1);
                String msg = et.getText().toString() + "\n";
                et.setText(""); // This is one way to reset the input box.
                //tv.append("\t" + Integer.toString(counter) + ":" +  msg); // This is one way to display a string. don't handle self differently

                /*
                 * Note that the following AsyncTask uses AsyncTask.SERIAL_EXECUTOR, not
                 * AsyncTask.THREAD_POOL_EXECUTOR as the above ServerTask does. To understand
                 * the difference, please take a look at
                 * http://developer.android.com/reference/android/os/AsyncTask.html
                 */
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
                Log.v(TAG,"send click");
            }
        });
    }



    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {


        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            try {

                while(true) {
                    Socket s = serverSocket.accept();
                    InputStream is = s.getInputStream();

                    byte[] messageRecieved = new byte[128];
                    int messageLength = is.read(messageRecieved, 0, 128);
                    String temp = "";
                    for (int i = 0; i < messageLength; i++) {
                        temp += (char) messageRecieved[i];
                    }

                    String[] messages = {Integer.toString(counter)+":"+temp};
                    Log.e("received", temp);
                    Log.e("received", "" + messageLength);
                    publishProgress(messages); //onProgressUpdate(messages);


                    ContentValues toInsert = new ContentValues();
                    toInsert.put(KEY_FIELD, Integer.toString(counter));
                    toInsert.put(VALUE_FIELD, temp);
                    counter ++;

                    cr.insert(mUri,toInsert);

                    //send ack
                    String msgToSend = "ACK";
                    OutputStream os = s.getOutputStream();
                    byte [] ba = msgToSend.getBytes();
                    os.write(ba);
                    Log.e("ServerAck", msgToSend);

                }

            }catch(IOException e) {
                Log.e(TAG, "ServerTask socket IOException");
                return null;
            }

            //return null;
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\t\n");

            return;
        }
    }

    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     *
     * @author stevko
     *
     */
    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            String allPorts[] = {REMOTE_PORT0,REMOTE_PORT1,REMOTE_PORT2,REMOTE_PORT3,REMOTE_PORT4};

            String msgToSend = msgs[0];

            ContentValues toInsert = new ContentValues();
            toInsert.put(KEY_FIELD, Integer.toString(counter));
            toInsert.put(VALUE_FIELD, msgToSend);
            //counter ++; don't update own counter, do it in server

            cr.insert(mUri,toInsert);

            for (String remotePort: allPorts) {
                //if (remotePort.equals(msgs[1])) continue; don't handle self differently
                try {


                    Log.v("MyPort", msgs[1]);


                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort));


                    Log.e("sent", msgToSend);
                    OutputStream os = socket.getOutputStream();
                    byte[] ba = msgToSend.getBytes();
                    os.write(ba);

                    //wait for ack

                    InputStream is = socket.getInputStream();

                    String temp = "";
                    while (!temp.equals("ACK")) {
                        byte[] messageRecieved = new byte[128];
                        int messageLength = is.read(messageRecieved, 0, 128);
                        temp = "";
                        for (int i = 0; i < messageLength; i++) {
                            temp += (char) messageRecieved[i];
                        }
                        Log.e("ClientAck", temp);
                    }


                    //done waiting for ack

                    socket.close();
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException");
                    Log.e("MYAPP", "exception: " + e.toString());
                }
            }

            return null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}
