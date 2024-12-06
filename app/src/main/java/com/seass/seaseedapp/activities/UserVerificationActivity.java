package com.seass.seaseedapp.activities;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.seass.seaseedapp.R;
import com.seass.seaseedapp.utils.AppUtils;

/**
 * Activity for user verification, for users to confirm their account.
 */
public class UserVerificationActivity extends AppCompatActivity {

    // UI elements
    Button btnResendVerification;
    ImageView btnBack;
    SwipeRefreshLayout swipeRefreshLayout;

    // Firebase authentication
    FirebaseAuth mAuth;
    FirebaseUser mUser;

    // Timer for resend verification button
    CountDownTimer resendVerificationTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_verification);

        // Initialize UI elements
        btnResendVerification = findViewById(R.id.btnResendVerification);
        btnBack = findViewById(R.id.btnBack);

        // Initialize Firebase authentication
        mAuth = FirebaseAuth.getInstance();
        mUser = mAuth.getCurrentUser();

        // Initialize SwipeRefreshLayout
        swipeRefreshLayout = findViewById(R.id.swipeRefresh);

        // Start the resend verification timer
        resendVerificationTimer();
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppUtils.switchActivity(UserVerificationActivity.this, MainActivity.class, "rtl");
            }
        });

        // Verify button click listener

        // Resend verification button click listener
        btnResendVerification.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mUser.isEmailVerified()) {
                    // Send email verification
                    mUser.sendEmailVerification().addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void unused) {
                            Log.d("EmailVerification", "Email verification sent successfully");
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e("EmailVerification", "Error while sending email verification", e);
                            btnResendVerification.setError("Error while sending email verification");
                            btnResendVerification.setEnabled(true);
                        }
                    });

                    // Restart the resend verification timer
                    resendVerificationTimer();
                }
            }
        });

        // SwipeRefreshLayout refresh listener
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setProgressBackgroundColor(R.color.dark_primary);
            swipeRefreshLayout.setColorScheme(R.color.orange_primary);

            swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    // Reload user data
                    mUser.reload();
                    swipeRefreshLayout.setRefreshing(false); // Stop the refresh animation
                }
            });
        } else {
            Log.e("UserVerificationActivity", "SwipeRefreshLayout is null");
        }
    }

    // Set up and start the resend verification timer
    private void resendVerificationTimer() {
        String btnResendVerificationText = String.valueOf(btnResendVerification.getText());
        btnResendVerification.setEnabled(false);

        // Set the countdown timer for 2 minutes
        resendVerificationTimer = new CountDownTimer(2 * 60 * 1000, 1000) {
            @SuppressLint("DefaultLocale")
            public void onTick(long millisUntilFinished) {
                // Update the button text with the remaining time
                btnResendVerification.setText(String.format("%s (%d)", btnResendVerificationText, millisUntilFinished / 1000));
            }

            public void onFinish() {
                // Enable the button and reset the text
                btnResendVerification.setEnabled(true);
                btnResendVerification.setText(btnResendVerificationText);
            }
        }.start();
    }
}
