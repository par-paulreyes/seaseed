package com.seass.seaseedapp.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.seass.seaseedapp.R;
import com.seass.seaseedapp.utils.AppUtils;
import com.seass.seaseedapp.utils.DataCache;

import java.util.ArrayList;
import java.util.Objects;

import jp.wasabeef.glide.transformations.RoundedCornersTransformation;

public class SessionPackageViewFragment extends Fragment {

    DataCache dataCache;

    // UI elements
    ImageView btnBack;
    TextView packageName, sessionDesc, itinerary, duration, includedServices,
             price;
    int packageColor;
    Button btnEditPackage, btnDeletePackage;

    // Firebase
    FirebaseAuth mAuth;
    FirebaseFirestore db;
    StorageReference storageReference;

    // Loading overlay
    View loadingOverlay;
    ProgressBar progressBar;

    // User ID received from arguments
    String userId, userType;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_session_package_view, container, false);
        dataCache = DataCache.getInstance();

        // Initialize UI elements
        packageName = view.findViewById(R.id.packageName);
        sessionDesc = view.findViewById(R.id.sessionDesc);
        itinerary = view.findViewById(R.id.itinerary);
        duration = view.findViewById(R.id.duration);
        includedServices = view.findViewById(R.id.includedServices);
        price = view.findViewById(R.id.price);

        btnEditPackage = view.findViewById(R.id.btnEditPackage);
        btnDeletePackage = view.findViewById(R.id.btnDeletePackage);
        btnBack = view.findViewById(R.id.btnBack);

        // Initialize Firebase instances
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        FirebaseStorage storage = FirebaseStorage.getInstance();
        storageReference = storage.getReference();

        // Initialize loading overlay elements
        loadingOverlay = view.findViewById(R.id.loadingOverlay);
        progressBar = view.findViewById(R.id.progressBar);

        // Retrieve user ID from arguments
        if (getArguments() != null) {
            userId = getArguments().getString("userId");
            packageName.setText(getArguments().getString("packageName"));

            if(Objects.equals(mAuth.getUid(), userId)) {
                btnEditPackage.setVisibility(View.VISIBLE);
                btnDeletePackage.setVisibility(View.VISIBLE);

            }
        }

        // Data not found in cache, fetch from the server
        loadDataFromFirebase(view);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(Objects.equals(mAuth.getUid(), userId))
                    AppUtils.switchFragment(SessionPackageViewFragment.this, new ThisProfileFragment());
                else{
                    OtherProfileFragment otherProfileFragment = new OtherProfileFragment();
                    Bundle args = new Bundle();
                    args.putString("userId", userId);
                    otherProfileFragment.setArguments(args);
                    AppUtils.switchFragment(SessionPackageViewFragment.this, otherProfileFragment);
                }

            }
        });

        btnDeletePackage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPackageDeletionDialog();
            }
        });

        btnEditPackage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SessionPackageEditFragment sessionPackageEditFragment = new SessionPackageEditFragment();
                Bundle args = new Bundle();
                args.putString("userId", userId);
                args.putString("packageName", packageName.getText().toString());
                args.putString("sessionDesc", sessionDesc.getText().toString());
                args.putString("itinerary", itinerary.getText().toString());
                args.putString("duration", duration.getText().toString());
                args.putString("includedServices", includedServices.getText().toString());
                args.putString("price", price.getText().toString());
                args.putInt("packageColor", packageColor);
                sessionPackageEditFragment.setArguments(args);
                AppUtils.switchFragment(SessionPackageViewFragment.this, sessionPackageEditFragment);


            }
        });

        return view;
    }


    /**
     * Open "Discard Changes" dialog.
     */
    private void showPackageDeletionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(requireContext().getResources().getString(R.string.confirmPackageDeletion));
        builder.setMessage(requireContext().getResources().getString(R.string.confirmPackageDeletionMessage));
        builder.setPositiveButton(requireContext().getResources().getString(R.string.delete_package), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                checkForActiveSessionBeforeDeletion();
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

    private void checkForActiveSessionBeforeDeletion() {
        if (userId != null && packageName.getText() != null) {
            // Reference to the Firestore document using the provided userId
            DocumentReference packageDocumentRef = db.collection("users")
                    .document(userId)
                    .collection("sessionPackages")
                    .document(packageName.getText().toString());

            // Query active tours to check if there are any associated with this package
            db.collection("users")
                    .document(userId)
                    .collection("activeSessions")
                    .whereEqualTo("sessionPackageRef", packageDocumentRef)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (!queryDocumentSnapshots.isEmpty()) {
                            // Active tours found, notify the user and prevent deletion
                            showActiveSessionsExistDialog();
                        } else {
                            // No active tours found, proceed with deletion
                            deletePackage();
                        }
                    })
                    .addOnFailureListener(e -> {
                        // Handle failure to query active tours
                        Log.e("DATABASE", "Error checking for active sessions", e);
                    });
        }
    }
    private void showActiveSessionsExistDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Active Sessions Exist");
        builder.setMessage("There are active sessions associated with this package.\nYou cannot delete it until all sessions are completed or canceled.");
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Dismiss the dialog
            }
        });
        builder.show();
    }

    private void deletePackage() {
        if (userId != null && packageName.getText() != null) {
            // Reference to the Firestore document using the provided userId
            DocumentReference packageDocumentRef = db.collection("users")
                    .document(userId)
                    .collection("sessionPackages")
                    .document(packageName.getText().toString());

            // Reference to the image file in Firebase Storage

            // Delete the package document and image
            packageDocumentRef.delete().addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    // Handle the failure to delete the package document
                    Log.e("DATABASE", "Error deleting package document", e);
                }
            });
        }
    }

    /**
     * Fetch user data from Firebase Firestore.
     */
    private void loadDataFromFirebase(View view) {
        if (userId != null) {
            // Reference to the Firestore document using the provided userId
            DocumentReference userDocumentRef = db.collection("users")
                    .document(userId);

            DocumentReference packageDocumentRef = userDocumentRef
                    .collection("sessionPackages")
                    .document(packageName.getText().toString());


            userDocumentRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                @Override
                public void onSuccess(DocumentSnapshot documentSnapshot) {
                    if (isAdded() && documentSnapshot.exists())
                        // Check if the user fields exist in the Firestore document
                        if (documentSnapshot.contains("type"))
                            userType = documentSnapshot.getString("type");


                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.e("DATABASE", "Error getting document", e);
                }
            });


            // Retrieve data from Firestore
            packageDocumentRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                @Override
                public void onSuccess(DocumentSnapshot documentSnapshot) {
                    if (isAdded() && documentSnapshot.exists()) {

                        if (documentSnapshot.contains("sessionDesc"))
                            sessionDesc.setText(documentSnapshot.getString("sessionDesc"));

                        if (documentSnapshot.contains("itinerary"))
                            itinerary.setText(documentSnapshot.getString("itinerary"));

                        if (documentSnapshot.contains("duration"))
                            duration.setText(documentSnapshot.getString("duration"));

                        if (documentSnapshot.contains("includedServices"))
                            includedServices.setText(documentSnapshot.getString("includedServices"));

                        if (documentSnapshot.contains("price"))
                            price.setText(documentSnapshot.getString("price"));

                        if (documentSnapshot.contains("packageColor"))
                            packageColor = Integer.parseInt(String.valueOf(documentSnapshot.get("packageColor")));
                    }
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.e("DATABASE", "Error getting document", e);
                }
            });
        }
    }

    private void enableUserLayout(){

    }

    private void enableFreeDiverLayout(){

    }

    /**
     * Show/hide the loading screen.
     */
    private void showLoading(boolean show) {
        if (isAdded()) {
            loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
}