package com.seass.seaseedapp.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.OnColorSelectedListener;
import com.flask.colorpicker.builder.ColorPickerClickListener;
import com.flask.colorpicker.builder.ColorPickerDialogBuilder;
import com.github.dhaval2404.imagepicker.ImagePicker;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.seass.seaseedapp.R;
import com.seass.seaseedapp.utils.AppUtils;
import com.seass.seaseedapp.utils.DataCache;
import com.seass.seaseedapp.utils.MultiSpinner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jp.wasabeef.glide.transformations.RoundedCornersTransformation;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class SessionPackageCreationFragment extends Fragment {

    private static final int MAX_PACKAGE_NAME_LENGTH = 27;
    DataCache dataCache;
    ImageView packageCoverImage, btnBack;
    Button btnCreatePackage;
    EditText packageNameInput, sessionDescInput, itineraryInput, durationInput,
            includedServicesInput, priceInput;
    int packageColor;

    List<String> selectedCountries;


    // Firebase
    FirebaseAuth mAuth;
    FirebaseUser mUser;
    FirebaseFirestore db;

    // Loading overlay
    View loadingOverlay;
    ProgressBar progressBar;

    // Color picker
    ConstraintLayout colorPicker;
    View colorView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_session_package_creation, container, false);
        dataCache = DataCache.getInstance();
        // Initialize UI elements
        btnBack = view.findViewById(R.id.btnBack);
        btnCreatePackage = view.findViewById(R.id.btnCreatePackage);
        packageNameInput = view.findViewById(R.id.packageNameInput);
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
        packageColor = getResources().getColor(R.color.orange_primary);

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
                                colorView.setBackgroundTintList(null);
                                colorView.setBackgroundTintList(ColorStateList.valueOf(selectedColor));
                                packageColor = selectedColor;
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
        btnCreatePackage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreatePackageDialog();
            }
        });

       // Set click listeners for buttons
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDiscardChangesDialog();
            }
        });

        return view;
    }


    /**
     * Save user data to Firebase Firestore.
     */
    private void saveUserDataToFirebase() {
        mUser = mAuth.getCurrentUser();
        if (mUser != null) {
            String userId = mUser.getUid();
            String packageName = packageNameInput.getText().toString().trim();
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
            userSessionPackageDocumentRef.set(packageData).addOnSuccessListener(new OnSuccessListener<Void>() {
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

    private void createPackage() {
        mUser = mAuth.getCurrentUser();
        if (mUser != null) {
            String userId = mUser.getUid();
            DocumentReference packageDocumentRef = db.collection("users")
                    .document(userId)
                    .collection("sessionPackages")
                    .document(packageNameInput.getText().toString().trim());

            packageDocumentRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            // Show an error message or handle the case where the package already exists
                            Toast.makeText(requireContext(), requireContext().getResources().getString(R.string.package_with_the_same_name_already_exists), Toast.LENGTH_LONG).show();
                            packageNameInput.setError(requireContext().getResources().getString(R.string.package_with_the_same_name_already_exists));

                        } else {
                            // Continue with the package creation process
                            saveUserDataToFirebase();
                            dataCache.clearCache();
                            AppUtils.switchFragment(SessionPackageCreationFragment.this, new ThisProfileFragment());
                        }
                    } else {
                        // Handle errors while querying the database
                        Log.e("DATABASE", "Error checking if package exists", task.getException());
                    }
                }
            });
        }
    }


    private boolean isPackageCreationValid() {
        return  AppUtils.isEditTextFilled(packageNameInput) &&
                AppUtils.isEditTextFilled(sessionDescInput) &&
                AppUtils.isEditTextFilled(itineraryInput) &&
                AppUtils.isEditTextFilled(durationInput) &&
                AppUtils.isEditTextFilled(includedServicesInput) &&
                AppUtils.isEditTextFilled(priceInput);
    }

    private void editTextInputManager() {
        packageNameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_PACKAGE_NAME_LENGTH)});

        // Add a TextWatcher for each EditText to enable/disable the button dynamically
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void afterTextChanged(Editable editable) {
                btnCreatePackage.setEnabled(isPackageCreationValid());
            }
        };

        // Set the TextWatcher for each EditText
        packageNameInput.addTextChangedListener(textWatcher);
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
                AppUtils.switchFragment(SessionPackageCreationFragment.this, new ThisProfileFragment());
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
    private void showCreatePackageDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(requireContext().getResources().getString(R.string.createPackage));
        builder.setMessage(requireContext().getResources().getString(R.string.confirmPackageCreationMessage));
        builder.setPositiveButton(requireContext().getResources().getString(R.string.createPackage), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                createPackage();
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