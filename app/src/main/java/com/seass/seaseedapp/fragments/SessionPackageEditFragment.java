package com.seass.seaseedapp.fragments;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.OnColorSelectedListener;
import com.flask.colorpicker.builder.ColorPickerClickListener;
import com.flask.colorpicker.builder.ColorPickerDialogBuilder;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.seass.seaseedapp.R;
import com.seass.seaseedapp.utils.AppUtils;
import com.seass.seaseedapp.utils.DataCache;
import java.util.HashMap;
import java.util.Map;

public class SessionPackageEditFragment extends Fragment {

    DataCache dataCache;
    ImageView btnBack;
    Button btnSaveChanges;
    EditText sessionDescInput, itineraryInput, durationInput,
            includedServicesInput, priceInput;
    int packageColor;
    String packageName, userId;

    // Firebase
    FirebaseAuth mAuth;
    FirebaseUser mUser;
    FirebaseFirestore db;

    // Image picker
    ActivityResultLauncher<Intent> imagePickLauncher;
    Uri selectedImageUri;


    // Loading overlay
    View loadingOverlay;
    ProgressBar progressBar;

    // Color picker
    ConstraintLayout colorPicker;
    View colorView;



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_session_package_edit, container, false);
        dataCache = DataCache.getInstance();

        // Initialize UI elements
        btnBack = view.findViewById(R.id.btnBack);
        btnSaveChanges = view.findViewById(R.id.btnSaveChanges);
        sessionDescInput = view.findViewById(R.id.sessionDescInput);
        itineraryInput = view.findViewById(R.id.itineraryInput);
        durationInput = view.findViewById(R.id.durationInput);
        includedServicesInput = view.findViewById(R.id.includedServicesInput);
        priceInput = view.findViewById(R.id.priceInput);

        // Initialize Firebase instances
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Loading overlay
        loadingOverlay = view.findViewById(R.id.loadingOverlay);
        progressBar = view.findViewById(R.id.progressBar);
        colorPicker = view.findViewById(R.id.colorPicker);
        colorView = view.findViewById(R.id.colorView);
        packageColor = R.color.orange_primary;

        // Set up arguments manager
        argumentsManager();


        // Set up input managers
        editTextInputManager();


        colorPicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ColorPickerDialogBuilder
                        .with(requireContext())
                        .lightnessSliderOnly()
                        .wheelType(ColorPickerView.WHEEL_TYPE.CIRCLE)
                        .density(8)
                        .setOnColorSelectedListener(new OnColorSelectedListener() {
                            @Override
                            public void onColorSelected(int selectedColor) {
                                btnSaveChanges.setEnabled(true);
                            }
                        })
                        .setPositiveButton(R.string.confirm, new ColorPickerClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int selectedColor, Integer[] allColors) {
                                // Reset the colorView background tint to default
                                colorView.setBackgroundTintList(null);
                                colorView.setBackgroundTintList(ColorStateList.valueOf(selectedColor));
                                packageColor = selectedColor;
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .build()
                        .show();
            }
        });

        // Set click listeners for buttons
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnSaveChanges.isEnabled())
                    showDiscardChangesDialog();
                else {
                    SessionPackageViewFragment sessionPackageViewFragment = new SessionPackageViewFragment();
                    Bundle args = new Bundle();
                    args.putString("userId", userId);
                    args.putString("packageName", packageName);
                    sessionPackageViewFragment.setArguments(args);
                    AppUtils.switchFragment(SessionPackageEditFragment.this, sessionPackageViewFragment);
                }


            }
        });

        btnSaveChanges.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showConfirmChangesDialog();
            }
        });

        return view;
    }

    private void argumentsManager(){
        if (getArguments() != null) {
            userId = getArguments().getString("userId");
            packageName = getArguments().getString("packageName");
            btnSaveChanges.setEnabled(false);
            sessionDescInput.setText(getArguments().getString("sessionDesc"));
            itineraryInput.setText(getArguments().getString("itinerary"));
            durationInput.setText(getArguments().getString("duration"));
            includedServicesInput.setText(getArguments().getString("includedServices"));
            priceInput.setText(getArguments().getString("price"));
            packageColor = getArguments().getInt("packageColor");
            colorView.setBackgroundTintList(null);
            colorView.setBackgroundTintList(ColorStateList.valueOf(packageColor));


        }
    }

    /**
     * Save user data to Firebase Firestore.
     */
    private void saveUserDataToFirebase() {
        mUser = mAuth.getCurrentUser();
        if (mUser != null) {
            String userId = mUser.getUid();
            String sessionDesc = sessionDescInput.getText().toString().trim();
            String itinerary = itineraryInput.getText().toString().trim();
            String duration = durationInput.getText().toString().trim();
            String includedServices = includedServicesInput.getText().toString().trim();
            String price = priceInput.getText().toString().trim();

            // Create a map to store user data
            Map<String, Object> packageData = new HashMap<>();
            packageData.put("sessionDesc", sessionDesc);
            packageData.put("itinerary", itinerary);
            packageData.put("duration", duration);
            packageData.put("includedServices", includedServices);
            packageData.put("price", price);
            packageData.put("packageColor", packageColor);
            // Set the document name as the user ID
            DocumentReference userSessionPackageDocumentRef = db.collection("users").
                    document(userId)
                    .collection("sessionPackages")
                    .document(packageName);

            // Set the data to the Firestore document
            userSessionPackageDocumentRef.update(packageData).addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Log.d("DATABASE", "DocumentSnapshot added with ID: " + userId);
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.w("DATABASE", "Error adding document", e);
                }
            });
        }
    }

    private boolean isPackageEditValid() {
        return  AppUtils.isEditTextFilled(sessionDescInput) &&
                AppUtils.isEditTextFilled(itineraryInput) &&
                AppUtils.isEditTextFilled(durationInput) &&
                AppUtils.isEditTextFilled(includedServicesInput) &&
                AppUtils.isEditTextFilled(priceInput);


    }

    private void editTextInputManager() {
        // Add a TextWatcher for each EditText to enable/disable the button dynamically
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                btnSaveChanges.setEnabled(isPackageEditValid());
            }
        };

        // Set the TextWatcher for each EditText
        sessionDescInput.addTextChangedListener(textWatcher);
        itineraryInput.addTextChangedListener(textWatcher);
        durationInput.addTextChangedListener(textWatcher);
        includedServicesInput.addTextChangedListener(textWatcher);
        priceInput.addTextChangedListener(textWatcher);
    }

    /**
     * Open "Discard Changes" dialog.
     */
    private void showDiscardChangesDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(requireContext().getResources().getString(R.string.confirmDiscardChanges));
        builder.setMessage(requireContext().getResources().getString(R.string.confirmDiscardChangesMessage));
        builder.setPositiveButton(requireContext().getResources().getString(R.string.confirm), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SessionPackageViewFragment sessionPackageViewFragment = new SessionPackageViewFragment();
                Bundle args = new Bundle();
                args.putString("userId", userId);
                args.putString("packageName", packageName);
                sessionPackageViewFragment.setArguments(args);
                AppUtils.switchFragment(SessionPackageEditFragment.this, sessionPackageViewFragment);
            }
        });

        builder.setNegativeButton(requireContext().getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Do nothing, close the dialog
            }
        });

        builder.show();
    }

    /**
     * Open "Confirm Changes" dialog.
     */
    private void showConfirmChangesDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(requireContext().getResources().getString(R.string.confirmChanges));
        builder.setMessage(requireContext().getResources().getString(R.string.confirmChangesMessage));
        builder.setPositiveButton(requireContext().getResources().getString(R.string.apply), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                saveUserDataToFirebase();
                btnSaveChanges.setEnabled(false);
                dataCache.clearCache();
                AppUtils.switchFragment(SessionPackageEditFragment.this, new ThisProfileFragment());

            }
        });

        builder.setNegativeButton(requireContext().getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Do nothing, close the dialog
            }
        });
        builder.show();
    }
}