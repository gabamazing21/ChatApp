package com.example.lernin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final String ANONYMOUS ="anonymous";
    public static final int DEFAULT_MSG_LIMIT =1000;

    private static final int RC_SIGN_IN = 1 ;
    private static final int RC_PHOTO_PICKER = 2 ;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText  mMessageEditText;
    private String mUsername;
    private Button mSendButton ;

    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mDatabaseReference;

    private FirebaseAuth mFirebaseAuth ;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    private ChildEventListener mChildEventListener ;
    private FirebaseStorage mFireBaseStorage ;
    private StorageReference mChatPhotoStorageReference;





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;

        //Getting instance of Firebae component
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFireBaseStorage = FirebaseStorage.getInstance();

        //Getting firebase componess reference
        mDatabaseReference = mFirebaseDatabase.getReference().child("messages");
        mChatPhotoStorageReference = mFireBaseStorage.getReference().child("chat_photos");



        //Initiate reference to views

        mMessageEditText = findViewById(R.id.messageEditText);
        mMessageListView = findViewById(R.id.messageListView);
        mProgressBar = findViewById(R.id.progressBar);
        mPhotoPickerButton = findViewById(R.id.photoPicker);
        mSendButton = findViewById(R.id.sendButton);

        List<FriendlyMessage> friendlyMessages = new ArrayList<>();

        mMessageAdapter = new MessageAdapter(this,R.layout.item_message,friendlyMessages);

        mMessageListView.setAdapter(mMessageAdapter);




        //Iniatialize the progress bar

        mProgressBar.setVisibility(View.INVISIBLE);

        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY,true);
                startActivityForResult(Intent.createChooser(intent,"Complete Action Using"),RC_PHOTO_PICKER);

                // TODO: Fire an intent to show an image picker
            }
        });




        // Enable Send button when there's text to send

        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

                if(s.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                }else{

                    mSendButton.setEnabled(false);
                }

            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });


        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LIMIT)});

        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Send messages on click

                FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);

                mDatabaseReference.push().setValue(friendlyMessage);

                // Clear input box
                mMessageEditText.setText("");
            }

        });


        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();

                if(user !=null){

                    onSignedInIniatialized(user.getDisplayName());

                }else {

                    onSignedOutCleanUp();



                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setAvailableProviders(Arrays.asList(
                                            new AuthUI.IdpConfig.GoogleBuilder().build(),
                                            new AuthUI.IdpConfig.EmailBuilder().build()))
                                    .setIsSmartLockEnabled(false)
                                    .build(),
                            RC_SIGN_IN);

                }

            }
        };


    }




    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
       switch (item.getItemId()) {
            case R.id.sign_out_menu:
                AuthUI.getInstance().signOut(this);
                return true ;
             default:
                 return onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if( mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
        detachedReadDatabaseListener();
        mMessageAdapter.clear();
    }

    private void onSignedInIniatialized(String displayName) {
        mUsername = displayName;
        attachedReadDatabaseListener();


    }


    private void onSignedOutCleanUp(){
        mUsername = ANONYMOUS;
        detachedReadDatabaseListener();
        mMessageAdapter.clear();

    }

    private void attachedReadDatabaseListener(){

        if( mChildEventListener  == null) {

            mChildEventListener = new ChildEventListener() {

                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                    mMessageAdapter.add(friendlyMessage);

                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            };


            mDatabaseReference.addChildEventListener(mChildEventListener);
        }


    }

    private void detachedReadDatabaseListener(){
        if(mChildEventListener != null) {
            mDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == RC_SIGN_IN){
            if(resultCode == RESULT_OK){

                Toast.makeText(MainActivity.this,"You are signed ",Toast.LENGTH_SHORT).show();
            }else if(resultCode == RESULT_CANCELED){

                Toast.makeText(MainActivity.this,"Login not successful",Toast.LENGTH_SHORT).show();
                finish();

            }
        }


        if(requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK){

            Uri imageSelected = data.getData();

            final StorageReference photoRef = mChatPhotoStorageReference.child(imageSelected.getLastPathSegment());

            UploadTask uploadTask = photoRef.putFile(imageSelected);

            Task<Uri> urlTask = uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                @Override
                public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }

                    // Continue with the task to get the download URL
                    return photoRef.getDownloadUrl();
                }
            }).addOnCompleteListener(new OnCompleteListener<Uri>() {
                @Override
                public void onComplete(@NonNull Task<Uri> task) {
                    if (task.isSuccessful()) {
                        Uri downloadUri = task.getResult();
                        Log.i(LOG_TAG,"Upload success",task.getException());

                        FriendlyMessage friendlyMessage = new FriendlyMessage(null,mUsername,downloadUri.toString());
                        mDatabaseReference.push().setValue(friendlyMessage);

                    } else {
                        // Handle failures
                        Log.i(LOG_TAG,"Upload Not Successful success",task.getException());
                        // ...
                    }
                }
            });
        }


    }
}
