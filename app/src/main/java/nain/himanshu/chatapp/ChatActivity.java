package nain.himanshu.chatapp;

import android.app.ProgressDialog;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import okhttp3.internal.Util;

public class ChatActivity extends AppCompatActivity {

    private String USERID, CONVERSATIONID, OTHERID;
    private Bundle bundle;

    private Toolbar mToolbar;
    private TextView mOtherName, mTyping;
    private CircleImageView mOtherProfilePic;

    private LinearLayout mChatLayout;
    private LayoutInflater mLayoutInflater;

    private EditText mMessage;
    private Button mSend;

    private RequestQueue mRequestQueue;

    /*
    TODO:this will listen to only new message for now, extend to typing.
     */
    private Socket mSocket;

    @Override
    protected void onStart() {
        super.onStart();
        USERID = getSharedPreferences(Config.LoginPrefs, MODE_PRIVATE).getString("id","");
        mSocket = MyApplication.getSocket();
        if(!mSocket.connected()){
            mSocket.connect();
        }
    }

    private Emitter.Listener onConnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mSocket.emit("log me", USERID);
                }
            });
        }
    };

    private Emitter.Listener onNewMessage = new Emitter.Listener() {
        @Override
        public void call(Object... args) {

            final JSONObject data = (JSONObject) args[0];

            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    try {
                        String sender = data.getString("author");
                        String sender_name = data.getString("name");
                        String sender_pic = data.getString("profilePic");
                        String conversationId = data.getString("conversationId");
                        String message = data.getString("message");

                        if(conversationId.equals(CONVERSATIONID)){

                            if(!sender.equals(USERID)){
                                addOtherMessage(message, null);
                            }
                        }else {

                            String title = sender_name + " send you a message";
                            Utils.sendNotification(title, message, Config.CHAT_NOTIF_CHANNEL,getApplicationContext());

                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }



                }
            });

        }
    };

    @Override
    protected void onResume() {
        super.onResume();

        mSocket.on("new message", onNewMessage);
        mSocket.on(Socket.EVENT_CONNECT, onConnect);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSocket.off("new message", onNewMessage);
        mSocket.off(Socket.EVENT_CONNECT, onConnect);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        mRequestQueue = Volley.newRequestQueue(this);
        if(getIntent().getExtras()!=null){

            bundle = getIntent().getExtras();
            CONVERSATIONID = bundle.getString("conversationId");
            if(CONVERSATIONID == null || CONVERSATIONID.isEmpty()){
                OTHERID = bundle.getString("other_id");
            }

        }else {
            finish();
            Toast.makeText(this, "Not enough details to start conversation", Toast.LENGTH_SHORT).show();

        }

        mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        mTyping = findViewById(R.id.typing);
        mTyping.setVisibility(View.GONE);

        mOtherName = findViewById(R.id.name);
        mOtherName.setText(bundle.getString("other_name"));
        mOtherProfilePic = findViewById(R.id.profilePic);

        if(!(Objects.requireNonNull(bundle.getString("other_pic")).isEmpty())){

            Glide.with(this)
                    .load(bundle.getString("other_pic"))
                    .apply(new RequestOptions()
                    .signature(new ObjectKey(String.valueOf(System.currentTimeMillis()))))
                    .into(mOtherProfilePic);

        }else {
            Glide.with(this).clear(mOtherProfilePic);
            mOtherProfilePic.setImageResource(R.drawable.demo_photo);
        }

        mChatLayout = findViewById(R.id.chatLayout);
        mLayoutInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

        mMessage = findViewById(R.id.message);
        mSend = findViewById(R.id.send);

        mSend.setOnClickListener(onSendClickListener);

        if(!(CONVERSATIONID == null || CONVERSATIONID.isEmpty())) {
            LOAD_MESSAGES();
        }
    }

    private void LOAD_MESSAGES() {

        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("Loading your chats..");
        dialog.setCancelable(false);
        dialog.show();

        JSONObject object = new JSONObject();
        try {
            object.put("conversationId", CONVERSATIONID);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                Config.GET_CONVERSATION,
                object,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {

                        try {
                            if(response.getBoolean("success")){

                                JSONArray array = response.getJSONArray("conversation");
                                JSONObject message;
                                String body, author;

                                for (int i=0; i<array.length();i++){

                                    message = array.getJSONObject(i);
                                    body = message.getString("body");
                                    author = message.getString("author");
                                    if(author.equals(USERID)){
                                        addSelfMessage(body, null);
                                    }else {
                                        addOtherMessage(body, null);
                                    }

                                }
                                dialog.dismiss();

                            }else {
                                Toast.makeText(getApplicationContext(), "Could not get messages", Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        dialog.dismiss();
                        Toast.makeText(getApplicationContext(), "Network Error", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
        );
        mRequestQueue.add(request);

    }

    private View.OnClickListener onSendClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            String message = mMessage.getText().toString();
            if(message.isEmpty()){
                mSend.setError("Message cannot be empty");
            }else {

                //SEND MESSAGE i.e check conversationId
                if(CONVERSATIONID == null || CONVERSATIONID.isEmpty()){

                    //start new conversation

                    attemptStartConversation(message);

                }else {

                    attemptSendMessage(message);

                }

            }

        }
    };

    private void attemptSendMessage(final String message) {

        mMessage.setText("");

        JSONObject params = new JSONObject();

        try {
            params.put("message", message);
            params.put("sender_id", USERID);
            params.put("conversationId", CONVERSATIONID);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                Config.SEND_MESSAGE,
                params,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {

                        try {
                            if (response.getBoolean("success")) {

                                JSONObject object = new JSONObject();
                                object.put("sender_id", USERID);
                                object.put("conversationId", CONVERSATIONID);
                                object.put("message", message);

                                mSocket.emit("new message", object);

                                addSelfMessage(message, null);

                            }else {

                                mMessage.setText(message);
                                Toast.makeText(getApplicationContext(),"Could not send message", Toast.LENGTH_SHORT).show();

                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        mMessage.setText(message);
                        Toast.makeText(getApplicationContext(),"Network error", Toast.LENGTH_SHORT).show();
                    }
                }
        );
        mRequestQueue.add(request);

    }

    private void attemptStartConversation(final String message) {

        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("Starting conversation...");
        dialog.setCancelable(false);
        dialog.show();

        JSONObject params = new JSONObject();
        try {
            params.put("sender_id", USERID);
            params.put("recipient_id", OTHERID);
            params.put("message", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                Config.START_CONVERSATION,
                params,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        dialog.dismiss();

                        try {
                            if(response.getBoolean("success")){

                                mMessage.setText("");

                                CONVERSATIONID = response.getString("conversationId");

                                addSelfMessage(message, null);

                                //join room
                                mSocket.emit("join", CONVERSATIONID);

                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        dialog.dismiss();

                        Toast.makeText(getApplicationContext(),"Failed sending message. Network error", Toast.LENGTH_SHORT).show();

                    }
                }
        );

        mRequestQueue.add(request);

    }

    /*
    TODO:IGNORING MESSAGE TIME FOR NOW ADD LATER
     */
    private void addSelfMessage(@NonNull String message, @Nullable String time){

        View view = mLayoutInflater.inflate(R.layout.self_message, null);

        TextView mMessageText = view.findViewById(R.id.message);
        TextView mTime = view.findViewById(R.id.time);
        mMessageText.setText(message);
        if(time == null){
            mTime.setVisibility(View.GONE);
        }else {
            mTime.setText(time);
        }

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(350, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(5,5,5,5);
        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        mChatLayout.addView(view, params);
        view.requestFocus();
    }

    private void addOtherMessage(@NonNull String message, @Nullable String time){

        View view = mLayoutInflater.inflate(R.layout.other_message, null);
        TextView mMessageText = view.findViewById(R.id.message);
        TextView mTime = view.findViewById(R.id.time);
        mMessageText.setText(message);
        if(time == null){
            mTime.setVisibility(View.GONE);
        }else {
            mTime.setText(time);
        }
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(350, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(5,5,5,5);
        mChatLayout.addView(view, params);
        view.requestFocus();

    }
}