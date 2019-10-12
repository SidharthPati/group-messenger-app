package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
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

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StreamCorruptedException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.*;

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
    static int finalSequenceNumber = 0;
    static String[] failedAvdClient = new String[1];
    boolean avdFlag = false;
    static int defaultTimeout = 3000;
    static String[] ports = {REMOTE_PORT0, REMOTE_PORT1, REMOTE_PORT2, REMOTE_PORT3, REMOTE_PORT4};

    private static void sendFailedAVDInfo(String portNum){
        Log.d(TAG, "Failed AVD in sendFailedAVDInfo(): "+portNum);
        for (String portNo: ports){
            if (failedAvdClient[0] != null && failedAvdClient[0].equals(portNo)) {
                Log.d(TAG, "This port: "+portNo+" has failed. So skipping it.");
                continue;
            }
            try {
                Log.d(TAG, "Sending failed AVD info to: "+portNo);
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(portNo));
                socket.setSoTimeout(defaultTimeout);
                PrintWriter out = new PrintWriter(socket.getOutputStream());
                out.write("Failed AVD:|"+ portNum+"\n");
                out.flush();
                socket.close();
            }
            catch(UnknownHostException uhe){
                Log.e(TAG, "Unknown Host Exception in sendFailedAVDInfo()");
                uhe.printStackTrace();
            }
            catch(IOException ioe){
                Log.e(TAG, "IO Exception in sendFailedAVDInfo()");
                ioe.printStackTrace();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        /*
         * Calculate the port number that this AVD listens on.
         * It is just a hack that I came up with to get around the networking limitations of AVDs.
         * The explanation is provided in the PA1 spec.
         */
        // Reference: Lines 57-59 from SimpleMessenger code
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        // Server Socket
        try {
            Log.d(TAG, "Trying to create ServerSocket object");
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            serverSocket.setReuseAddress(true);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Printing stack trace");
            e.printStackTrace();
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        final TextView tv = (TextView) findViewById(R.id.textView1);
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
        // Referred this code from template of SimpleMessenger
        // https://stackoverflow.com/questions/5588804/android-button-setonclicklistener-design
        final EditText editText = (EditText) findViewById(R.id.editText1);
        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText.getText().toString() + "\n";
                editText.setText(""); // Resetting input text box
                tv.append("\t\t\t\t\t\t\t" + msg); // Display the message
                tv.append("\n");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
            }
        });

    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        // Key for every message
        int keySequence = 0;
        // Sequence number for proposals
        Double sequenceNumber = 0.0;
        Boolean finalFlag = false;
        // HashMap for storing Indices and messages
        HashMap<Integer, String> messageIndex = new HashMap<Integer, String>();
        // HashMap to store AVD process numbers to be used to append while sending proposals
        HashMap<String, Double> avdProcessNum = new HashMap<String, Double>(){{
            put(REMOTE_PORT0, 0.0);
            put(REMOTE_PORT1, 0.1);
            put(REMOTE_PORT2, 0.2);
            put(REMOTE_PORT3, 0.3);
            put(REMOTE_PORT4, 0.4);
        }};
        // HashMap to store messages originating from a port
        HashMap<String, ArrayList<Integer>> msgPortMap = new HashMap<String, ArrayList<Integer>>(){{
            put(REMOTE_PORT0, new ArrayList<Integer>());
            put(REMOTE_PORT1, new ArrayList<Integer>());
            put(REMOTE_PORT2, new ArrayList<Integer>());
            put(REMOTE_PORT3, new ArrayList<Integer>());
            put(REMOTE_PORT4, new ArrayList<Integer>());
        }};
        // Map for delivery status, 0 for undeliverable, 1 for deliverable
        HashMap<Integer, Integer> deliveryMap = new HashMap<Integer, Integer>();
        // Implementation of Priority Queue as HashMap
        HashMap<Integer, Double> priorityMap = new HashMap<Integer, Double>();
        // Temporary HashMap to sort
        HashMap<Integer, Double> sortedTemp;

        private synchronized void deleteStuckMessages(){
            // Method to delete messages that are stuck for long time
            // Check what messages can be delivered
            ArrayList<Integer> list = new ArrayList<Integer>();
            Iterator it = priorityMap.entrySet().iterator();
            while (it.hasNext()) {
                HashMap.Entry pair = (HashMap.Entry) it.next();
                int currKey = 0;
                currKey = Integer.parseInt(pair.getKey().toString());
                String seqNum = pair.getValue().toString();
                if (deliveryMap.get(currKey) == 1) {
                    // Pass the message to onProgressUpdate()
                    Log.d(TAG, "messageIndex during insertion:");
                    System.out.println(Collections.singletonList(messageIndex));
                    if (messageIndex.get(currKey) != null) {
                        publishProgress(messageIndex.get(currKey));
                        list.add(Integer.parseInt(pair.getKey().toString()));
                    }
                } else {
                    priorityMap.remove(currKey);
                    // Check if this is needed and is causing problems
                    messageIndex.remove(currKey);
                    deliveryMap.remove(currKey);
                }
            }
            // Remove messages from priority queue (HashMap) that have been delivered
            for (Integer i : list){
                priorityMap.remove(i);
                // Check if this is needed and is causing problems
                messageIndex.remove(i);
                deliveryMap.remove(i);
            }
            list.clear();
            Log.d(TAG, "Priority Queue after removal: ");
            System.out.println(Collections.singletonList(priorityMap));
        }

        private HashMap<Integer, Double> sortByComparator(HashMap<Integer, Double> unsortMap)
        {
            // Sort HashMap based on values
            // Reference: https://stackoverflow.com/a/13913206/10316954
            List<Entry<Integer, Double>> list = new LinkedList<Entry<Integer, Double>>(unsortMap.entrySet());

            // Sorting the list based on values
            Collections.sort(list, new Comparator<Entry<Integer, Double>>()
            {
                public int compare(Entry<Integer, Double> o1,
                                   Entry<Integer, Double> o2)
                {
                    return o1.getValue().compareTo(o2.getValue());
                }
            });

            // Maintaining insertion order with the help of LinkedList
            HashMap<Integer, Double> sortedMap = new LinkedHashMap<Integer, Double>();
            for (Entry<Integer, Double> entry : list)
            {
                sortedMap.put(entry.getKey(), entry.getValue());
            }

            return sortedMap;
        }


        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];


            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */

            while (true) {
                try {
                    // BufferedReader to receive messages
                    Log.d(TAG, "Received message from client");
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(3000);
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));
                    String strReceived = in.readLine();
                    Log.d(TAG, "String received from client is: "+strReceived);

                    // When asked for proposal
                    if (strReceived.contains("Propose|")){
                        // This block is to send proposals
                        String[] splitString = strReceived.split("\\|");
                        String originPortNum = splitString[1];
                        Log.d(TAG, "Origin Portnumber is : "+originPortNum);
                        String msg = splitString[3];

                        // If message is coming from a failed port then don't send a proposal
                        if (failedAvdClient[0] != null && failedAvdClient[0].equals(originPortNum))
                            continue;

                        // Store message in messageIndex for Indexing with keySequence
                        messageIndex.put(++keySequence, msg);
                        // Mark this message as undeliverable initially
                        deliveryMap.put(keySequence, 0);
                        // Store this message's key in port based on origin of message
                        msgPortMap.get(originPortNum).add(keySequence);

                        // Create proposal and send it
                        Double portNumInDouble = avdProcessNum.get(splitString[2]);
                        sequenceNumber++;
                        Double proposalNum = sequenceNumber+portNumInDouble;
                        Log.d(TAG, "Proposal Number being sent is: "+proposalNum.toString());
//                        sequenceNumber++;

                        // Store this proposal number in Priority Queue(HashMap) and sort it
                        priorityMap.put(keySequence, proposalNum);
                        sortedTemp = sortByComparator(priorityMap);
                        priorityMap = (HashMap) sortedTemp.clone();

                        // Send Proposal to Client
                        PrintWriter out = new PrintWriter(socket.getOutputStream());
                        Log.d(TAG, "Sending Proposal");
                        out.print("Proposal|"+proposalNum.toString()+"\n");
                        out.flush();

                        Log.d(TAG, "Printing contents of messageIndex: ");
                        System.out.println(Collections.singletonList(messageIndex));
                        Log.d(TAG, "Printing contents of deliveryMap: ");
                        System.out.println(Collections.singletonList(deliveryMap));
                        Log.d(TAG, "Printing contents of priorityMap after sorting: ");
                        System.out.println(Collections.singletonList(priorityMap));
                        Log.d(TAG, "Printing contents of msgPortMap: ");
                        System.out.println(Collections.singletonList(msgPortMap));
                    }

                    // When client sends final proposal
                    if (strReceived.contains("Final Proposal|")){
                        // This block handles the case when final proposal is sent
                        Log.d(TAG, "Final proposal check in server");
                        String[] splitString = strReceived.split("\\|");
                        String finalProposal = splitString[1];
                        String portNumber = splitString[2];
                        String msg = splitString[3];
                        int key = 0;
                        for (Entry<Integer, String> entry : messageIndex.entrySet()) {
                            if (entry.getValue().equals(msg)) {
                                key = entry.getKey();
                                break;
                            }
                        }

                        if (key == 0){
                            continue;
                        }
                        Log.d(TAG, "Key retrieved is: "+Integer.toString(key));
                        Log.d(TAG, "Final proposal obtained is: "+finalProposal);

                        // Update final proposal in priority queue (HashMap)
                        priorityMap.put(key, Double.parseDouble(finalProposal));
                        // Mark this msg as deliverable in deliveryMap - make it 1
                        deliveryMap.put(key, 1);

                        // Sort the priorityMap
                        sortedTemp = sortByComparator(priorityMap);
                        priorityMap = (HashMap) sortedTemp.clone();
                        Log.d(TAG, "Sorted priorityMap after final proposal is: ");
                        System.out.println(Collections.singletonList(priorityMap));
                        Log.d(TAG, "Delivery map is: ");
                        System.out.println(Collections.singletonList(deliveryMap));

                        // Check what messages can be delivered
                        Log.d(TAG, "Printing contents of messageIndex: ");
                        System.out.println("Message Index: "+Collections.singletonList(messageIndex));
                        Log.d(TAG, "Printing contents of deliveryMap: ");
                        System.out.println("Delivery Map: "+Collections.singletonList(deliveryMap));
                        Log.d(TAG, "Printing contents of priorityMap after sorting: ");
                        System.out.println("Priority Map: "+Collections.singletonList(priorityMap));

                        ArrayList<Integer> list = new ArrayList<Integer>();
                        Iterator it = priorityMap.entrySet().iterator();
                        while (it.hasNext()){
                            HashMap.Entry pair = (HashMap.Entry)it.next();
                            int currKey = 0;
                            currKey = Integer.parseInt(pair.getKey().toString());
                            if (deliveryMap.get(currKey) == 1 && messageIndex.get(currKey) != null){
                                // Pass the message to onProgressUpdate()
                                publishProgress(messageIndex.get(currKey));
                                list.add(Integer.parseInt(pair.getKey().toString()));
                            }
                            else{
                                break;
                            }
                        }
                        // Remove messages from priority queue that have been delivered
                        for (Integer i : list){
                            priorityMap.remove(i);
                            // Check if this is needed and is causing problems
                            messageIndex.remove(i);
                            deliveryMap.remove(i);
                        }
                        list.clear();
                        Log.d(TAG, "Priority Queue after removal: ");
                        System.out.println(Collections.singletonList(priorityMap));

                        // Update sequenceNumber
                        Double temp1 = Double.parseDouble(finalProposal);
                        int temp = temp1.intValue();
                        Double tempDouble = Double.parseDouble(Integer.toString(temp));
                        sequenceNumber = Math.max(sequenceNumber, tempDouble);
                        Log.d(TAG, "Updated sequence number is : ");
                        System.out.println(sequenceNumber);
                        finalFlag = true;
                    }

                    // Receive Failed AVDs
                    // This block handles the scenario when an AVD fails
                    if (strReceived.contains("Failed AVD:|")){
                        Log.d(TAG, "In failed AVDs");
                        String[] splitString = strReceived.split("\\|");
                        String failedPortNum = splitString[1];
                        failedAvdClient[0] = failedPortNum;
                        Log.d(TAG, "Printing failed AVD in server: "+failedAvdClient[0]);
                        avdFlag = true;
                        ArrayList<Integer> failedAVDMessages = msgPortMap.get(failedPortNum);
                        // Remove failed AVD's messages from priorityMap
                        for(Integer msgInd : failedAVDMessages){
                            priorityMap.remove(msgInd);
                            messageIndex.remove(msgInd);
                            deliveryMap.remove(msgInd);
                        }
                        // Sort the priorityMap
                        Log.d(TAG, "Sorting the priorityMap after receiving failed AVDs info");
                        sortedTemp = sortByComparator(priorityMap);
                        priorityMap = (HashMap) sortedTemp.clone();
                        Log.d(TAG, "Sorted priorityMap after final proposal is: ");
                        System.out.println(Collections.singletonList(priorityMap));
                        Log.d(TAG, "Delivery map is: ");
                        System.out.println(Collections.singletonList(deliveryMap));

                        // Check what messages can be delivered
                        ArrayList<Integer> list = new ArrayList<Integer>();
                        Iterator it = priorityMap.entrySet().iterator();
                        while (it.hasNext()) {
                            HashMap.Entry pair = (HashMap.Entry) it.next();
                            int currKey = 0;
                            currKey = Integer.parseInt(pair.getKey().toString());
                            String seqNum = pair.getValue().toString();
                            if (deliveryMap.get(currKey) == 1) {
                                // Pass the message to onProgressUpdate()
                                Log.d(TAG, "messageIndex during insertion:");
                                System.out.println(Collections.singletonList(messageIndex));
                                if (messageIndex.get(currKey) != null) {
                                    publishProgress(messageIndex.get(currKey));
                                    list.add(Integer.parseInt(pair.getKey().toString()));
                                }
                            } else {
                                break;
                            }
                        }
                        // Remove messages from priority queue that have been delivered
                        for (Integer i : list){
                            priorityMap.remove(i);
                            // Check if this is needed and is causing problems
                            messageIndex.remove(i);
                            deliveryMap.remove(i);
                        }
                        list.clear();
                        Log.d(TAG, "Priority Queue after removal: ");
                        System.out.println(Collections.singletonList(priorityMap));
                    }

                    // Send Acknowledgment to Client
                    if (finalFlag) {
                        PrintWriter out = new PrintWriter(socket.getOutputStream());
                        Log.d(TAG, "sending acknowledgement");
                        out.print("acknowledge" + "\n");
                        out.flush();
                    }
                    finalFlag = false;

                } catch (SocketTimeoutException ste){
                    Log.e(TAG, "Socket timeout Exception in ServerTask");
                    deleteStuckMessages();
                }
                catch (IOException e) {
                    Log.e(TAG, "Receive message failed in ServerTask");
                }
            }
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\t\n");

            /* References:
             * Lines Referred from OnPTestClickListener.java
             *
             */
            final Uri mUri;
            final ContentValues mContentValue;


            mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
            mContentValue = new ContentValues();
            String keyToBeInserted = Integer.toString(finalSequenceNumber);
            mContentValue.put("key",keyToBeInserted);
            mContentValue.put("value",strReceived);

            // Inserting values
            try {
                getContentResolver().insert(mUri, mContentValue);
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }

            finalSequenceNumber++;

        }
        // Function taken from OnPTestClickListener.java
        private Uri buildUri(String scheme, String authority) {
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.authority(authority);
            uriBuilder.scheme(scheme);
            return uriBuilder.build();
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {
        Double maxValue = 0.0;
        int proposalCount = 0;
        ArrayList<String> receivedProposalsAVD = new ArrayList<String>();

        @Override
        protected Void doInBackground(String... msgs) {
            // This block sends message to all other clients, handles all proposals and sends final proposal
            for (String portNumber : ports) {
                try {
                    if (failedAvdClient[0] != null) {
                        Log.d(TAG, "Array of failed AVDs: "+failedAvdClient[0]);
                    }
                    // Do not connect to this port if it has already failed
                    if (failedAvdClient[0] != null && failedAvdClient[0].equals(portNumber))
                        continue;
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(portNumber));
                    socket.setSoTimeout(defaultTimeout);

                    String msgToSend = msgs[0].trim();
                    String portMsgOrigin = msgs[1];
                    Log.d(TAG, "Origin of message is at this port: "+portMsgOrigin);
                    Log.d(TAG, "Message that has been typed: "+msgToSend);
                    /*
                     * TODO: Fill in your client code that sends out a message.
                     */

                    // Send message and ask for proposal from Clients
                    try {
                        PrintWriter out = new PrintWriter(socket.getOutputStream());
                        out.write("Propose|" + portMsgOrigin + "|"+portNumber+"|"+ msgToSend + "\n");
                        out.flush();

                        // Get Proposals
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(socket.getInputStream()));
                        String str = in.readLine();
                        Log.d(TAG, "Message from Server is: "+str);

                        // If string returned is null
                        if(str == null){
                            Log.d(TAG, "Proposal received for the message "+
                                    msgToSend+ "is null. This port: "+portNumber+" is dead");
                            if (failedAvdClient[0] == null) {
                                failedAvdClient[0] = portNumber;
                                sendFailedAVDInfo(portNumber);
                                Log.d(TAG, "List of failed AVDs when processing the " +
                                        "message: " + msgToSend);
                                System.out.println(failedAvdClient[0]);
                            }
                        }

                        // When Proposal is returned
                        if (str != null && str.contains("Proposal|")) {
                            proposalCount++;
                            String[] splitString = str.split("\\|");
                            Double proposal = Double.parseDouble(splitString[1]);
                            Log.d(TAG, "Proposal received is: "+proposal.toString());
                            if (proposal > maxValue)
                                maxValue = proposal;
                        }
                        Log.d(TAG, "Proposal count for the message "+msgToSend+
                                "is : " + Integer.toString(proposalCount));
                        Log.d(TAG, "Max value for the message " +msgToSend+
                                "is : " + Double.toString(maxValue));

                        // Close the socket after receiving an acknowledgment from server
                        if (str!=null && str.equals("acknowledge")) {
                            Log.d(TAG,"Socket close");
                            socket.close();
                        }
                    }catch (SocketTimeoutException e) {
                        Log.e(TAG, "Socket timed out for port: " + portNumber);
                        e.printStackTrace();
                        if (failedAvdClient[0] == null) {
                            failedAvdClient[0] = portNumber;
                            sendFailedAVDInfo(portNumber);
                        }
                        Log.d(TAG, "Port Failed: "+portNumber);
                        System.out.println(failedAvdClient[0]);
                    } catch (IOException ioe) {
                        Log.e(TAG, "IOException in Socket");
                        ioe.printStackTrace();
                        if (failedAvdClient[0] == null) {
                            failedAvdClient[0] = portNumber;
                            sendFailedAVDInfo(portNumber);
                        }
                        Log.d(TAG, "Port Failed: "+portNumber);
                        System.out.println(failedAvdClient[0]);
                    }

                    // Send final proposal
                    if ((failedAvdClient[0]!=null &&
                            (proposalCount+failedAvdClient.length >= 5)) ||
                            (failedAvdClient[0] == null && proposalCount == 5)) {
                        Log.d(TAG, " Proposal count for message "+msgToSend+
                                "check in client: "+portNumber+" is 5. Sending final" +
                                "proposal");
                        for (String portNumber1 : ports) {
                            try {
                                if (failedAvdClient[0]!=null && failedAvdClient[0].equals(portNumber1))
                                    continue;
                                Socket socket1 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                        Integer.parseInt(portNumber1));
                                socket1.setSoTimeout(defaultTimeout);
                                PrintWriter out1 = new PrintWriter(socket1.getOutputStream());
                                out1.write("Final Proposal|" + Double.toString(maxValue) + "|" + portNumber1 + "|" + msgToSend+"\n");
                                out1.flush();
                                socket1.close();
                            }
                            catch (SocketTimeoutException e) {
                                Log.e(TAG, "Socket timed out for port: " + portNumber);
                                e.printStackTrace();
                                if (failedAvdClient[0] == null) {
                                    failedAvdClient[0] = portNumber;
                                    sendFailedAVDInfo(portNumber);
                                }
                                Log.d(TAG, "Port Failed: "+portNumber);
                                System.out.println(failedAvdClient[0]);
                            } catch (IOException ioe) {
                                Log.e(TAG, "IOException in Socket");
                                ioe.printStackTrace();
                                if (failedAvdClient[0] == null) {
                                    failedAvdClient[0] = portNumber;
                                    sendFailedAVDInfo(portNumber);
                                }
                                Log.d(TAG, "Port Failed: "+portNumber);
                                System.out.println(failedAvdClient[0]);
                            }
                        }
                    }
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                    if (failedAvdClient[0] == null) {
                        failedAvdClient[0] = portNumber;
                        sendFailedAVDInfo(portNumber);
                    }
                    Log.d(TAG, "Port Failed: "+portNumber);
                    System.out.println(failedAvdClient[0]);
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException");
                    if (failedAvdClient[0] == null) {
                        failedAvdClient[0] = portNumber;
                        sendFailedAVDInfo(portNumber);
                    }
                    Log.d(TAG, "Port Failed: "+portNumber);
                    System.out.println(failedAvdClient[0]);
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
