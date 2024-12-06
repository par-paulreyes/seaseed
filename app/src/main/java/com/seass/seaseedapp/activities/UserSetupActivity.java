package com.seass.seaseedapp.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.seass.seaseedapp.R;
import com.seass.seaseedapp.utils.AppUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Activity for user setup, allowing users to input profile information.
 */
public class UserSetupActivity extends AppCompatActivity {

    // Constants
    private static final short MAX_USERNAME_LENGTH = 12;

    // UI elements
    Spinner spinnerGender;
    Button btnDone;
    EditText inputUsername, inputBirthDate, inputBio;
    ImageView btnBack;

    // Calendar for birth date selection
    Calendar calendar;
    DatePickerDialog datePickerDialog;

    // Firebase
    FirebaseAuth mAuth;
    FirebaseUser mUser;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_setup);

        // Initialize Firebase instances
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        // Initialize UI elements
        inputUsername = findViewById(R.id.inputUsername);
        inputBio = findViewById(R.id.inputBio);
        spinnerGender = findViewById(R.id.spinnerGender);
        inputBirthDate = findViewById(R.id.inputBirthDate);

        // Initialize calendar for birth date selection
        calendar = Calendar.getInstance();

        btnDone = findViewById(R.id.btnDone);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppUtils.switchActivity(UserSetupActivity.this, MainActivity.class, "rtl");
            }
        });

        // Date picker dialog setup
        DatePickerDialog.OnDateSetListener date = new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, month);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                updateCalendar();

                boolean isUsernameValid = !inputUsername.getText().toString().isEmpty()
                        && inputUsername.length() <= MAX_USERNAME_LENGTH
                        && inputUsername.getText().toString().matches("^(?=.*[a-zA-Z0-9])[a-zA-Z0-9 ]+$");
                boolean isGenderSelected = spinnerGender.getSelectedItemPosition() > 0;
                boolean isBirthDateSelected = !inputBirthDate.getText().toString().isEmpty();

                btnDone.setEnabled(isUsernameValid && isGenderSelected && isBirthDateSelected);
            }

            private void updateCalendar() {
                String format = "dd/MM/yyyy";
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
                inputBirthDate.setText(sdf.format(calendar.getTime()));
            }
        };

        datePickerDialog = new DatePickerDialog(UserSetupActivity.this,
                android.R.style.Theme_Holo_Dialog_NoActionBar_MinWidth,
                date, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));

        // Set date picker limits
        Calendar maxDateCalendar = Calendar.getInstance();
        maxDateCalendar.add(Calendar.YEAR, -16);
        datePickerDialog.getDatePicker().setMaxDate(maxDateCalendar.getTimeInMillis());

        Calendar minDateCalendar = Calendar.getInstance();
        minDateCalendar.add(Calendar.YEAR, -100);
        datePickerDialog.getDatePicker().setMinDate(minDateCalendar.getTimeInMillis());

        // InputBirthDate click listener to show date picker
        inputBirthDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                datePickerDialog.show();
            }
        });

        // Gender spinner setup
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.genders,
                R.layout.spinner_custom_layout
        );

        adapter.setDropDownViewResource(R.layout.spinner_custom_dropdown_layout);

        spinnerGender.setAdapter(adapter);

        spinnerGender.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                boolean isUsernameValid = !inputUsername.getText().toString().isEmpty()
                        && inputUsername.length() <= MAX_USERNAME_LENGTH
                        && inputUsername.getText().toString().matches("^(?=.*[a-zA-Z0-9])[a-zA-Z0-9 ]+$");
                boolean isGenderSelected = position > 0;
                boolean isBirthDateSelected = !inputBirthDate.getText().toString().isEmpty();

                btnDone.setEnabled(isUsernameValid && isGenderSelected && isBirthDateSelected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        // Username input length filter
        inputUsername.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_USERNAME_LENGTH)});

        // Username text watcher for enabling/disabling the done button
        inputUsername.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                boolean isUsernameValid = !editable.toString().trim().isEmpty()
                        && editable.toString().trim().length() <= MAX_USERNAME_LENGTH
                        && editable.toString().trim().matches("^(?=.*[a-zA-Z0-9])[a-zA-Z0-9 ]+$");
                boolean isGenderSelected = spinnerGender.getSelectedItemPosition() > 0;
                boolean isBirthDateSelected = !inputBirthDate.getText().toString().isEmpty();

                btnDone.setEnabled(isUsernameValid && isGenderSelected && isBirthDateSelected);
            }
        });

        // Done button click listener
        btnDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveUserDataToFirebase();

                // After saving user data, start the UserHomeActivity
                AppUtils.switchActivity(UserSetupActivity.this, UserHomeActivity.class, "ltr");
                finish();
            }
        });
    }

    /**
     * Save user data to Firebase Firestore.
     */
    private void saveUserDataToFirebase() {
        mUser = mAuth.getCurrentUser();

        if (mUser != null) {
            String userId = mUser.getUid();
            String username = inputUsername.getText().toString().trim();
            String email = mUser.getEmail();
            String gender = getResources().getStringArray(R.array.genders)[spinnerGender.getSelectedItemPosition()];
            String birthDate = inputBirthDate.getText().toString().trim();
            String bio = inputBio.getText().toString().trim();
            // Create a map to store user data
            Map<String, Object> userData = new HashMap<>();
            userData.put("language", "en");
            userData.put("username", username);
            userData.put("email", email);
            if(!bio.isEmpty()) userData.put("bio", bio); else userData.put("bio", "");
            userData.put("gender", gender);
            userData.put("birthDate", birthDate);
            userData.put("type", "User");


            // Set the document name as the user ID
            DocumentReference userDocumentRef = db.collection("users").document(userId);

            // Set the data to the Firestore document
            userDocumentRef.set(userData).addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Log.d("DATABASE","DocumentSnapshot added with ID: " + userId);
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.w("DATABASE", "Error adding document", e);
                }
            });
        }
    }

}
