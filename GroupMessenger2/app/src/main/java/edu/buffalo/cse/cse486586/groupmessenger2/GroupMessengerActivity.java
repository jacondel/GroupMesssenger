package edu.buffalo.cse.cse486586.groupmessenger2;

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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;

import static java.lang.Math.max;

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

    private char DELIMITER = (char)0x01;
    private int localSequenceNumber = 0;
    private String _myPort;
    private int nextSequenceNumberToDecide = 0;
    private String _badPort = "";


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
        mUri  = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
        cr = getContentResolver();

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        _myPort = myPort;


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

    private class PQElement {
        public int sourcePid;
        public int sequenceNumber;
        public String contents;
        public Socket socket;

        public  PQElement(int p, int s,String c, Socket soc){
            sourcePid = p;
            sequenceNumber = s;
            contents = c;
            socket = soc;
        }
    }

    private class DQElement {
        public int sourcePid;
        public int sequenceNumber;
        public String contents;
        public boolean deliverable;
        public int localSequenceNumber;

        public  DQElement(int p, int s,String c,int l, boolean b){
            sourcePid = p;
            sequenceNumber = s;
            contents = c;
            localSequenceNumber = l;
            deliverable = b;
        }
    }

    private class PQ{

        private ArrayList<PQElement> arr;

        public PQ(){
            arr = new ArrayList<PQElement>();
        }

        public void add(PQElement e){
            arr.add(e);
        }

        private PQElement getLowestByPid(int pid){
            PQElement res = null;
            int minSequenceNumber = Integer.MAX_VALUE;

            for(int i=0; i<arr.size(); i++){
                if(arr.get(i).sourcePid==pid && arr.get(i).sequenceNumber < minSequenceNumber){
                    res = arr.get(i);
                    minSequenceNumber = arr.get(i).sequenceNumber;
                }
            }

            return res;
        }

        public PQElement peekLowestByPid(int pid){
            return getLowestByPid(pid);
        }
        public void popLowestByPid(int pid){
            arr.remove(getLowestByPid(pid));
        }
    }

    private class DQ{

        private ArrayList<DQElement> arr;

        public DQ(){
            arr = new ArrayList<DQElement>();
        }

        public void add(DQElement e){
            arr.add(e);
        }

        private DQElement getLowest(){
            DQElement res = null;
            int minSequenceNumber = Integer.MAX_VALUE;

            for(int i=0; i<arr.size(); i++){
                if(arr.get(i).sequenceNumber < minSequenceNumber){
                    res = arr.get(i);
                    minSequenceNumber = arr.get(i).sequenceNumber;
                }
            }

            return res;
        }

        public DQElement peekLowest(){
            return getLowest();
        }
        public void popLowest(){
            arr.remove(getLowest());
        }

        public void update(int p,int l, int n){
            for (DQElement e : arr){
                if(e.sourcePid == p && e.localSequenceNumber == l){
                    e.sequenceNumber = n;
                    e.deliverable = true;
                }
            }
        }

        public void removeMessagesByPid(int badpid){
            ArrayList <DQElement> toRemove = new ArrayList<DQElement>();
            for (DQElement e : arr){
                if(e.sourcePid == badpid){
                    toRemove.add(e);
                }
            }
            for (DQElement e : toRemove){
                arr.remove(e);
            }
        }
    }




    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        private int expectingNext[] = new int[5];

        private int pidLookup(String pid){
            if(pid.equals(REMOTE_PORT0)) return 0;
            if(pid.equals(REMOTE_PORT1)) return 1;
            if(pid.equals(REMOTE_PORT2)) return 2;
            if(pid.equals(REMOTE_PORT3)) return 3;
            if(pid.equals(REMOTE_PORT4)) return 4;
            return -1;

        }

        public ServerTask(){
            expectingNext = new int[]{0,0,0,0,0};
        }

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];


            int sequenceNumberToPropose = 0;
            int actualSequenceNumber = 0;
            PQ pq = new PQ();
            DQ dq = new DQ();

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

                    String splits[]=temp.split(""+DELIMITER);
                    String type = splits[0];
                    // TYPE 1 Proposal Request
                    if (type.equals("1")) {
                        int sourcePid = pidLookup(splits[1]);
                        int senderSequenceNumber = Integer.parseInt(splits[2]);
                        String messageContents = splits[3];

                        int myport = pidLookup(_myPort);

                        Log.e("My Port In Server", ""+myport);
                        Log.e("received", temp);
                        Log.e("splits[0]", splits[0]);
                        Log.e("In Server", "sourcePid: " + sourcePid + " senderSequenceNumber: " + senderSequenceNumber);


                        if (senderSequenceNumber == expectingNext[sourcePid]) { // what we're expecting
                            //propose on this
                            String msgToSend = "" + ((sequenceNumberToPropose++)*10 + myport);
                            OutputStream os = s.getOutputStream();
                            byte[] ba = msgToSend.getBytes();
                            os.write(ba);
                            dq.add(new DQElement(sourcePid,sequenceNumberToPropose,messageContents,senderSequenceNumber,false));
                            Log.e("Released", msgToSend);
                            expectingNext[sourcePid] += 1;

                            //and free all others from q
                            while (null != pq.peekLowestByPid(sourcePid) && pq.peekLowestByPid(sourcePid).sequenceNumber == expectingNext[sourcePid]) {
                                PQElement top = pq.peekLowestByPid(sourcePid);
                                pq.popLowestByPid(sourcePid);

                                msgToSend = "" + ((sequenceNumberToPropose++)*10 + myport);
                                os = top.socket.getOutputStream();
                                ba = msgToSend.getBytes();
                                os.write(ba);
                                dq.add(new DQElement(sourcePid,sequenceNumberToPropose,messageContents,senderSequenceNumber,false));
                                Log.e("Freed", msgToSend);
                                expectingNext[sourcePid] += 1;
                            }

                        } else if (senderSequenceNumber > expectingNext[sourcePid]) { //missed a message from sender
                            //add to q
                            Log.d("Server", "PUSH");
                            pq.add(new PQElement(sourcePid, senderSequenceNumber, messageContents, s));
                        }

                    }else{//TYPE 2 Actual Sequence Number
                        String message = splits[1];
                        int maxProposalNumber = Integer.parseInt(splits[2]);
                        int pid = pidLookup(splits[3]);
                        int senderSequenceNumber = Integer.parseInt(splits[4]);

                        int myport = pidLookup(_myPort);

                        if(!_badPort.equals("")){
                            int badPort = pidLookup(_badPort);
                            dq.removeMessagesByPid(badPort);
                        }

                        dq.update(pid,senderSequenceNumber,maxProposalNumber); //update the actual sequence number in dq


                        // free all marked as deliverable at the top

                        while(null != dq.peekLowest() && dq.peekLowest().deliverable){
                            DQElement top = dq.peekLowest();
                            dq.popLowest();

                            Log.e("popping", ""+top.sequenceNumber);
                            String b = "rest ";
                            for(DQElement e : dq.arr ){
                                b = b + e.sequenceNumber + ",";
                            }
                            Log.e("popping2", b);

                            //String messages[] = { actualSequenceNumber+ ": " + message};
                            String messages[] = { actualSequenceNumber + ": " + top.sequenceNumber+ ": " + top.contents};
                            publishProgress(messages); //onProgressUpdate(messages);

                            ContentValues toInsert = new ContentValues();
                            toInsert.put(KEY_FIELD, Integer.toString(actualSequenceNumber));
                            toInsert.put(VALUE_FIELD, top.contents);
                            cr.insert(mUri,toInsert);

                            sequenceNumberToPropose = max(sequenceNumberToPropose,(top.sequenceNumber/10) + 1);
                            sequenceNumberToPropose += 1;
                            actualSequenceNumber += 1;

                        }





                    }






                    /*ContentValues toInsert = new ContentValues();
                    toInsert.put(KEY_FIELD, Integer.toString(counter));
                    toInsert.put(VALUE_FIELD, temp);
                    counter ++;

                    cr.insert(mUri,toInsert);*/

                    //String[] messages = {Integer.toString(sequenceNumberToPropose*10 + myport) + ":" + messageContents};
                    //publishProgress(messages); //onProgressUpdate(messages);

                    //send ack
                    /*try {
                        Thread.sleep(600); // cause timeout exception
                    }catch(Exception e){
                        Log.e(TAG,"Server timeout error");
                    }*/

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
            //String allPorts[] = {REMOTE_PORT0,REMOTE_PORT1,REMOTE_PORT2,REMOTE_PORT3,REMOTE_PORT4};
            ArrayList<String> allPorts = new ArrayList<String>();
            allPorts.add(REMOTE_PORT0);
            allPorts.add(REMOTE_PORT1);
            allPorts.add(REMOTE_PORT2);
            allPorts.add(REMOTE_PORT3);
            allPorts.add(REMOTE_PORT4);

            int localSequenceNumberSnapshot = localSequenceNumber++;

            String msgToSend = msgs[0];
            //String pid = "" + msgs[1].charAt(msgs[1].length()-1);
            String pid = msgs[1];

            ArrayList<Integer> receivedProposals = new ArrayList<Integer>();
            String badPort = "";

            for (String remotePort: allPorts) {
                try {


                    Log.v("MyPort", msgs[1]);
                    Log.v("MyPID",pid);


                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort));

                    socket.setSoTimeout(1000);

                    Log.e("Sending", msgToSend);
                    OutputStream os = socket.getOutputStream();
                    byte[] ba = ("1" + DELIMITER + pid + DELIMITER +  localSequenceNumberSnapshot + DELIMITER + msgToSend).getBytes();
                    os.write(ba);

                    //wait for ack

                    InputStream is = socket.getInputStream();

                    String temp = "";

                    byte[] messageRecieved = new byte[128];
                    int messageLength = is.read(messageRecieved, 0, 128);
                    for (int i = 0; i < messageLength; i++) {
                        temp += (char) messageRecieved[i];
                    }

                    if (!temp.equals("")) {
                        int proposedSequenceNumber = Integer.parseInt(temp);
                        Log.e(TAG, "Received Proposal " + proposedSequenceNumber);
                        receivedProposals.add(proposedSequenceNumber);
                    }else{
                        badPort = remotePort;
                        _badPort = remotePort;
                    }

                    //done waiting for ack

                    socket.close();
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                } catch( SocketTimeoutException e){
                    Log.e(TAG, "ClientTask Timeout");
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException");
                    Log.e("MYAPP", "exception: " + e.toString());
                }
            }
            if (!badPort.equals("")){
                Log.e("EMPTY","removing: " + badPort);
                allPorts.remove(badPort);
                badPort = "";
            }


            int maxProposalNumber = -1;
            for (int i : receivedProposals){
                maxProposalNumber = max(maxProposalNumber,i);
            }

            Log.e("Client max proposal" ,""+ maxProposalNumber);



            try {
                for (String remotePort: allPorts) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort));

                    socket.setSoTimeout(1000);

                    Log.e("Sending", msgToSend);
                    OutputStream os = socket.getOutputStream();
                    byte[] ba = ("2" + DELIMITER + msgToSend + DELIMITER + maxProposalNumber + DELIMITER + pid + DELIMITER + localSequenceNumberSnapshot).getBytes();
                    os.write(ba);
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch( SocketTimeoutException e){
                Log.e(TAG, "ClientTask Timeout");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
                Log.e("MYAPP", "exception: " + e.toString());
            }

            nextSequenceNumberToDecide += 1;
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
