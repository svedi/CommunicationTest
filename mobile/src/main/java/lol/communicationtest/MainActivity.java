package lol.communicationtest;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.Set;

public class MainActivity extends AppCompatActivity {

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void sendMessage(View view) {
        EditText editText = (EditText) findViewById(R.id.edit_message);
        String message = editText.getText().toString();

        PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/hello_world");
        putDataMapReq.getDataMap().putString(HELLO_WORLD_KEY, message);
        PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
        putDataReq.setUrgent();
        PendingResult<DataApi.DataItemResult> pendingResult =
                Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
    }

    private static final String HELLO_WORLD_KEY = "lol.communicationtest.hello_world";

    private GoogleApiClient mGoogleApiClient;
    private final DataApi.DataListener onDataChangeListener;

    {
        onDataChangeListener = new DataApi.DataListener() {
            @Override
            public void onDataChanged(DataEventBuffer dataEventBuffer) {
                setText("Data changed");
            }
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(@Nullable Bundle bundle) {
                        Wearable.DataApi.addListener(mGoogleApiClient, onDataChangeListener);
                        setText("Connected");
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                        setText("Connection suspended");
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                        setText("Connection suspended");
                    }
                })
                .build();

        PendingResult<CapabilityApi.GetCapabilityResult> capabilityResult =
                Wearable.CapabilityApi.getCapability(
                        mGoogleApiClient, CLEARABLE_CAPABILITY_NAME,
                        CapabilityApi.FILTER_REACHABLE);

        capabilityResult.setResultCallback(new ResultCallback<CapabilityApi.GetCapabilityResult>() {
            @Override
            public void onResult(@NonNull CapabilityApi.GetCapabilityResult result) {
                updateTranscriptionCapability(result.getCapability());
            }
        });

        CapabilityApi.CapabilityListener capabilityListener =
                new CapabilityApi.CapabilityListener() {
                    @Override
                    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
                        updateTranscriptionCapability(capabilityInfo);
                    }
                };

        Wearable.CapabilityApi.addCapabilityListener(
                mGoogleApiClient,
                capabilityListener,
                CLEARABLE_CAPABILITY_NAME);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
        setText("Resume");
    }


    @Override
    protected void onPause() {
        super.onPause();
        Wearable.DataApi.removeListener(mGoogleApiClient, onDataChangeListener);
        mGoogleApiClient.disconnect();
    }

    private void setText(String s) {
        TextView text = (TextView) findViewById(R.id.text);
        text.setText(s + "\n" + text.getText());
    }

    //
    //  Message API
    //

    private static final String
            CLEARABLE_CAPABILITY_NAME = "clearable";

    private String clearNodeId = null;

    private void updateTranscriptionCapability(CapabilityInfo capabilityInfo) {
        Set<Node> connectedNodes = capabilityInfo.getNodes();

        clearNodeId = pickBestNodeId(connectedNodes);
    }

    private String pickBestNodeId(Set<Node> nodes) {
        String bestNodeId = null;
        // Find a nearby node or pick one arbitrarily
        System.out.println("Nodes found:");
        for (Node node : nodes) {
            System.out.println(node.getId());
            if (node.isNearby()) {
                return node.getId();
            }
            bestNodeId = node.getId();
        }
        return bestNodeId;
    }

    public static final String CLEAR_MESSAGE_PATH = "/clear";

    public void clear(View view) {
        if (clearNodeId != null) {
            Wearable.MessageApi.sendMessage(mGoogleApiClient, clearNodeId,
                    CLEAR_MESSAGE_PATH, new byte[0]).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                @Override
                public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                    if (!sendMessageResult.getStatus().isSuccess()) {
                        System.out.println("failed to send message");
                    }
                }
            });
        } else {
            System.out.println("no capable node connected");
            PendingResult<NodeApi.GetConnectedNodesResult> result = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient);
            result.setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                @Override
                public void onResult(@NonNull NodeApi.GetConnectedNodesResult result) {
                    System.out.println("Connected nodes:");
                    for (Node n : result.getNodes()) {
                        System.out.println(n.getDisplayName() + ", " + n.getId());
                        Wearable.MessageApi.sendMessage(mGoogleApiClient, n.getId(),
                                CLEAR_MESSAGE_PATH, new byte[0]).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                            @Override
                            public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                                if (!sendMessageResult.getStatus().isSuccess()) {
                                    System.out.println("failed to send message");
                                }
                            }
                        });
                    }
                }
            });
        }
    }
}
