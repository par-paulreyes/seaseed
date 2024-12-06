package com.seass.seaseedapp.fragments;

import android.app.AlertDialog;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.chaek.android.RatingBar;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.MaterialCalendarView;
import com.prolificinteractive.materialcalendarview.OnDateSelectedListener;
import com.prolificinteractive.materialcalendarview.format.DateFormatTitleFormatter;
import com.seass.seaseedapp.R;
import com.seass.seaseedapp.adapters.ReviewRecyclerViewAdapter;
import com.seass.seaseedapp.adapters.SessionPackageRecyclerViewAdapter;
import com.seass.seaseedapp.decorators.EventDecorator;
import com.seass.seaseedapp.utils.ActiveSession;
import com.seass.seaseedapp.utils.AppUtils;
import com.seass.seaseedapp.utils.Chat;
import com.seass.seaseedapp.utils.DataCache;

import org.json.JSONArray;
import org.threeten.bp.LocalDate;
import org.threeten.bp.ZoneId;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import java.io.IOException;
import org.json.JSONObject;

public class OtherProfileFragment extends Fragment implements SessionPackageRecyclerViewAdapter.OnLoadCompleteListener {

    // Initialize DataCache
    DataCache dataCache;

    // UI elements
    ImageView profilePic;
    TextView username, bio, birthDate, gender, type,
            totalRating, noAvailableReviews, noAvailableSessionPackages,
            sessionPackagesORbookedSessionsHeadline, reviewsHeadline;
    Button btnAddReview, btnDM;
    com.chaek.android.RatingBar ratingBar;


    // Firebase
    FirebaseAuth mAuth;
    FirebaseFirestore db;
    StorageReference storageReference;

    // SwipeRefreshLayout for pull-to-refresh functionality
    SwipeRefreshLayout swipeRefreshLayout;

    // Loading overlay
    RelativeLayout userDataLoadingOverlay, calendarLoadingOverlay;

    // RecyclerView for Tour Packages
    RecyclerView recyclerViewSessions, recyclerViewReviews;
    SessionPackageRecyclerViewAdapter sessionPackageRecyclerViewAdapter;
    ReviewRecyclerViewAdapter reviewsRecyclerViewAdapter;
    List<String> sessionPackagesIdList;
    List<String> reviewsIdList;

    String otherUserId, thisUserId, thisUserType = "Free Diver", otherUserType;

    final LocalDate min = LocalDate.now(ZoneId.systemDefault());
    final LocalDate max = min.plusMonths(6);
    final String DATE_FORMAT = "yyyy-MM-dd";

    List<LocalDate> decoratedDatesList;
    List<ActiveSession> activeSessions;
    List<DocumentReference> bookedSessionsRefs;
    MaterialCalendarView calendarView;
    ConstraintLayout totalRatingLayout;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_other_profile, container, false);
        dataCache = DataCache.getInstance();
        // Initialize UI elements
        swipeRefreshLayout = view.findViewById(R.id.swipeRefresh);
        username = view.findViewById(R.id.username);
        bio = view.findViewById(R.id.bio);
        birthDate = view.findViewById(R.id.birthDate);
        gender = view.findViewById(R.id.gender);
        type = view.findViewById(R.id.type);
        btnAddReview = view.findViewById(R.id.btnAddReview);
        btnDM = view.findViewById(R.id.btnDM);

        ratingBar = view.findViewById(R.id.ratingBar);
        totalRating = view.findViewById(R.id.totalRating);
        totalRatingLayout = view.findViewById(R.id.totalRatingLayout);

        noAvailableReviews = view.findViewById(R.id.noAvailableReviews);
        noAvailableSessionPackages = view.findViewById(R.id.noAvailableSessionPackages);

        sessionPackagesORbookedSessionsHeadline = view.findViewById(R.id.sessionPackagesORbookedSessionsHeadline);
        reviewsHeadline = view.findViewById(R.id.reviewsHeadline);

        // Initialize Firebase instances
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        FirebaseStorage storage = FirebaseStorage.getInstance();
        storageReference = storage.getReference();

        // Initialize loading overlay elements
        userDataLoadingOverlay = view.findViewById(R.id.userDataLoadingOverlay);
        calendarLoadingOverlay = view.findViewById(R.id.calendarLoadingOverlay);

        recyclerViewReviews = view.findViewById(R.id.recyclerViewReviews);
        recyclerViewSessions = view.findViewById(R.id.recyclerViewSessions);
        calendarView = view.findViewById(R.id.calendarView);

        swipeRefreshLayout.setProgressBackgroundColor(R.color.dark_primary);
        swipeRefreshLayout.setColorScheme(R.color.orange_primary);

        sessionPackagesIdList = new ArrayList<>();
        reviewsIdList = new ArrayList<>();
        decoratedDatesList = new ArrayList<>();

        // Retrieve user ID from arguments
        if (getArguments() != null)
            otherUserId = getArguments().getString("userId");

        FirebaseUser mUser = mAuth.getCurrentUser();
        if (mUser != null)
            thisUserId = mUser.getUid();

        // Initialize calendar and set its attributes
        calendarView.setShowOtherDates(MaterialCalendarView.SHOW_ALL);
        calendarView.state().edit().setMinimumDate(min).setMaximumDate(max).commit();

        // Set the locale for the calendar view title formatter
        calendarView.setTitleFormatter(new DateFormatTitleFormatter());

        userDataLoadingOverlay.setVisibility(View.VISIBLE);
        calendarLoadingOverlay.setVisibility(View.VISIBLE);

        btnDM.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Code to be executed when the button is clicked
                // For example, you can call the function to create a new chat here
                enterChat(thisUserId, otherUserId, new OnChatCreatedListener() {
                    @Override
                    public void onChatCreated(Chat chat) {
                        // Chat created successfully, perform actions with chatId
                        Log.d("DATABASE", "Chat created successfully with ID: " + chat.getChatId());
                        // Replace fragment with ChatFragment
                        if (getActivity() != null) {
                            AppUtils.switchFragment(OtherProfileFragment.this, ChatFragment.newInstance(chat));
                        }
                    }

                    @Override
                    public void onChatExists(Chat chat) {
                        // Error occurred while creating chat, handle failure
                        if (getActivity() != null) {
                            getActivity().getSupportFragmentManager().beginTransaction()
                                    .replace(R.id.frameLayout, ChatFragment.newInstance(chat))
                                    .addToBackStack(null)
                                    .commit();
                        }
                    }

                    @Override
                    public void onChatCreateFailed(Exception e) {
                        // Error occurred while creating chat, handle failure
                        Log.e("DATABASE", "Error creating chat", e);
                    }
                });
            }
        });


        DocumentReference otherUserDocumentRef = db.collection("users").document(otherUserId);

        // Retrieve userType from Firestore, duplicated from loadDataFromFirebase in case there's an asynchronous delay
        otherUserDocumentRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot otherUserdocumentSnapshot) {
                if (isAdded() && otherUserdocumentSnapshot.exists()) {
                    // Check if the user type exists in the Firestore document
                    if (otherUserdocumentSnapshot.contains("type")) {
                        otherUserType = otherUserdocumentSnapshot.getString("type");
                        if (Objects.equals(otherUserType, "Free Diver")) {
                            freeDiver_sessionBookingManager();
                            freeDiver_reviewsManager();
                        }
                        else {
                            calendarView.setSelectionMode(MaterialCalendarView.SELECTION_MODE_SINGLE);
                            sessionPackagesORbookedSessionsHeadline.setText(R.string.my_booked_sessions);
                            user_sessionBookingManager();
                            user_reviewsManager();

                        }
                    }
                }
            }
        }).addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                DocumentReference thisUserDocumentRef = db.collection("users").document(thisUserId);

                // Retrieve data from Firestore
                thisUserDocumentRef.get()
                        .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                            @Override
                            public void onSuccess(DocumentSnapshot thisUserdocumentSnapshot) {
                                if (isAdded() && thisUserdocumentSnapshot.exists()) {

                                    thisUserDocumentRef.collection("reviews").document(otherUserId).get()
                                            .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                                @Override
                                                public void onSuccess(DocumentSnapshot reviewsDocumentSnapshot) {
                                                    if(reviewsDocumentSnapshot.exists())
                                                        btnAddReview.setVisibility(View.GONE);
                                                    else {
                                                        // Check if the user type exists in the Firestore document
                                                        if (thisUserdocumentSnapshot.contains("type")) {
                                                            thisUserType = thisUserdocumentSnapshot.getString("type");
                                                            if (Objects.equals(thisUserType, "User"))
                                                                btnAddReview.setVisibility(View.VISIBLE);
                                                            else
                                                                btnAddReview.setVisibility(View.GONE);
                                                        }
                                                        else
                                                            btnAddReview.setVisibility(View.GONE);

                                                    }
                                                }
                                            });

                                }
                            }
                        }).addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                            @Override
                            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                if(Objects.equals(thisUserType, "Free Diver"))
                                    btnAddReview.setVisibility(View.GONE);

                                if(Objects.equals(thisUserType, "User"))
                                    if(Objects.equals(otherUserType, "Free Diver"))
                                        btnAddReview.setOnClickListener(new View.OnClickListener() {
                                            @Override
                                            public void onClick(View v) {
                                                if(isAdded()) {
                                                    // Create the dialog
                                                    AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
                                                    View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_review, null);
                                                    builder.setView(dialogView);
                                                    AlertDialog dialog = builder.create();
                                                    final int[] rating = {9};
                                                    EditText review = dialogView.findViewById(R.id.review);
                                                    com.chaek.android.RatingBar ratingBar = dialogView.findViewById(R.id.ratingBar);
                                                    Button btnAddReview = dialogView.findViewById(R.id.btnAddReview);

                                                    ratingBar.setRatingBarListener(new RatingBar.RatingBarListener() {
                                                        @Override
                                                        public void setRatingBar(int i) {
                                                            if(i == 0)
                                                                rating[0] = 1;
                                                            else
                                                                rating[0] = i;


                                                        }
                                                    });

                                                    btnAddReview.setOnClickListener(new View.OnClickListener() {
                                                        @Override
                                                        public void onClick(View v) {
                                                            // Assuming you have the necessary variables available
                                                            String reviewText = String.valueOf(review.getText());
                                                            final String[] reviewerUsername = new String[1];
                                                            final String[] freeDiver = new String[1];
                                                            reviewerUsername[0] = "";
                                                            freeDiver[0] = "";

                                                            thisUserDocumentRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                                                @Override
                                                                public void onSuccess(DocumentSnapshot documentSnapshot) {
                                                                    if(documentSnapshot.contains("username"))
                                                                        reviewerUsername[0] = documentSnapshot.getString("username");
                                                                }
                                                            }).addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                                                @Override
                                                                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                                                    otherUserDocumentRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                                                        @Override
                                                                        public void onSuccess(DocumentSnapshot documentSnapshot) {
                                                                            if(documentSnapshot.contains("username"))
                                                                                freeDiver[0] = documentSnapshot.getString("username");
                                                                        }
                                                                    }).addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                                                        @Override
                                                                        public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                                                            Calendar calendar = Calendar.getInstance();
                                                                            Date currentTime = calendar.getTime();

                                                                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                                                                            String currentTimeAndDate = sdf.format(currentTime);
                                                                            final long[] oldRating = new long[1];
                                                                            oldRating[0] = -1;
                                                                            // Create a new review object
                                                                            Map<String, Object> reviewData = new HashMap<>();
                                                                            reviewData.put("freeDiver", freeDiver[0]);
                                                                            reviewData.put("rating", rating[0]);
                                                                            reviewData.put("review", reviewText);
                                                                            reviewData.put("reviewerUsername", reviewerUsername[0]);
                                                                            reviewData.put("time&date", currentTimeAndDate);

                                                                            // Reference to the reviews collection for the current user
                                                                            DocumentReference userReviewsRef = db.collection("users").document(thisUserId).collection("reviews").document(otherUserId);
                                                                            userReviewsRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                                                                @Override
                                                                                public void onSuccess(DocumentSnapshot documentSnapshot) {
                                                                                    if (documentSnapshot.exists())
                                                                                        oldRating[0] = documentSnapshot.getLong("rating");
                                                                                }
                                                                            }).addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                                                                @Override
                                                                                public void onSuccess(DocumentSnapshot documentSnapshot) {
                                                                                    // Add the review to Firestore
                                                                                    userReviewsRef.set(reviewData)
                                                                                            .addOnSuccessListener(documentReference -> {
                                                                                                //Log.d("DATABASE", "Review added with ID: " + otherUserId);
                                                                                                DocumentReference sessionGuideReviewsRef = db.collection("users").document(otherUserId).collection("reviews").document(thisUserId);
                                                                                                Map<String, Object> reviewRef = new HashMap<>();
                                                                                                reviewRef.put("reviewRef", userReviewsRef);
                                                                                                sessionGuideReviewsRef.set(reviewRef);


                                                                                                otherUserDocumentRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                                                                                    @Override
                                                                                                    public void onSuccess(DocumentSnapshot otherUserDocumentSnapshot) {

                                                                                                        float currentTotalRating = 0;
                                                                                                        if(otherUserDocumentSnapshot.contains("totalRating"))
                                                                                                            currentTotalRating = otherUserDocumentSnapshot.getLong("totalRating");

                                                                                                        if(oldRating[0] != -1)
                                                                                                            currentTotalRating -= oldRating[0];

                                                                                                        currentTotalRating+= rating[0];

                                                                                                        Map<String, Object> totalRating = new HashMap<>();
                                                                                                        totalRating.put("totalRating", currentTotalRating);
                                                                                                        otherUserDocumentRef.update(totalRating);

                                                                                                        Bundle args = new Bundle();
                                                                                                        args.putString("userId", otherUserDocumentRef.getId());
                                                                                                        OtherProfileFragment otherProfileFragment = new OtherProfileFragment();
                                                                                                        otherProfileFragment.setArguments(args);
                                                                                                        AppUtils.switchFragment(OtherProfileFragment.this, otherProfileFragment);
                                                                                                        dialog.dismiss();
                                                                                                    }
                                                                                                });
                                                                                            })
                                                                                            .addOnFailureListener(e -> {
                                                                                                Log.w("DATABASE", "Error adding review", e);
                                                                                                dialog.dismiss();
                                                                                                // Handle errors if review addition fails
                                                                                            });
                                                                                }
                                                                            });

                                                                        }
                                                                    });
                                                                }
                                                            });
                                                        }
                                                    });
                                                    dialog.show();
                                                }
                                            }
                                        });

                                    else
                                        btnAddReview.setVisibility(View.GONE);

                            }
                        }).addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                            @Override
                            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                loadDataFromFirebase(view);
                            }
                        });
            }
        });

        // Set up pull-to-refresh functionality
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setProgressBackgroundColor(R.color.dark_primary);
            swipeRefreshLayout.setColorScheme(R.color.orange_primary);

            swipeRefreshLayout.setOnRefreshListener(() -> {
                sessionPackagesIdList = new ArrayList<>();

                // Remove all existing decorators
                calendarView.removeDecorators();
                // Clear the list of decorated dates
                decoratedDatesList.clear();

                loadDataFromFirebase(view);
                swipeRefreshLayout.setRefreshing(false);
            });
        } else {
            Log.e("ThisProfileFragment", "SwipeRefreshLayout is null");
        }

        return view;
    }


    private void freeDiver_reviewsManager() {
        CollectionReference collectionReference = db.collection("users").document(otherUserId).collection("reviews");

        // Clear the list before adding new items
        reviewsIdList.clear();

        collectionReference.get().addOnSuccessListener(queryDocumentSnapshots -> {
            for (QueryDocumentSnapshot documentSnapshot : queryDocumentSnapshots)
                // Add document ID to the reviewsIdList
                reviewsIdList.add(documentSnapshot.getId());

            if (reviewsIdList != null) {
                // Move the current user's review to the top if it exists
                if (reviewsIdList.contains(thisUserId)) {
                    btnAddReview.setVisibility(View.GONE);
                    reviewsIdList.remove(thisUserId);
                    reviewsIdList.add(0, thisUserId);
                }
                else
                    btnAddReview.setVisibility(View.VISIBLE);


            }
            collectionReference.getParent().get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                @Override
                public void onSuccess(DocumentSnapshot documentSnapshot) {
                    if (documentSnapshot.contains("totalRating")) {
                        if(!reviewsIdList.isEmpty()){
                            float totalRatingValue = (float) documentSnapshot.getLong("totalRating") / (float) reviewsIdList.size();
                            totalRating.setText(String.format("%.1f", totalRatingValue/2.0));
                            ratingBar.setScore((Math.round(totalRatingValue)));
                            noAvailableReviews.setVisibility(View.GONE);
                            totalRatingLayout.setVisibility(View.VISIBLE);

                        }
                    }
                }
            });



            // Notify the adapter if needed
            if (reviewsRecyclerViewAdapter != null) {
                reviewsRecyclerViewAdapter.notifyDataSetChanged();
            }

            // Initialize and set up the adapter after fetching the data
            reviewsRecyclerViewAdapter = new ReviewRecyclerViewAdapter(getActivity(), requireContext(), reviewsIdList, otherUserId, false);
            recyclerViewReviews.setAdapter(reviewsRecyclerViewAdapter);
            recyclerViewReviews.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        }).addOnFailureListener(e -> {
            // Handle failure
        });
    }
    private void user_reviewsManager(){
        CollectionReference collectionReference = db.collection("users").document(otherUserId).collection("reviews");

        // Clear the list before adding new items
        reviewsIdList.clear();

        collectionReference.get().addOnSuccessListener(queryDocumentSnapshots -> {
            for (QueryDocumentSnapshot documentSnapshot : queryDocumentSnapshots) {
                // Add document ID to the reviewsIdList
                reviewsIdList.add(documentSnapshot.getId());
            }

            if(!reviewsIdList.isEmpty())
                noAvailableReviews.setVisibility(View.GONE);
            else
                noAvailableReviews.setVisibility(View.VISIBLE);

            // Notify the adapter if needed
            if (reviewsRecyclerViewAdapter != null) {
                reviewsRecyclerViewAdapter.notifyDataSetChanged();
            }

            // Initialize and set up the adapter after fetching the data
            reviewsRecyclerViewAdapter = new ReviewRecyclerViewAdapter(getActivity(), requireContext(), reviewsIdList, otherUserId, true);
            recyclerViewReviews.setAdapter(reviewsRecyclerViewAdapter);
            recyclerViewReviews.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        }).addOnFailureListener(e -> {
            // Handle failure
        });
    }
    private void user_sessionBookingManager(){
        calendarView.setOnDateChangedListener(new OnDateSelectedListener() {
            @Override
            public void onDateSelected(@NonNull MaterialCalendarView widget, @NonNull CalendarDay date, boolean selected) {
                if (decoratedDatesList.contains(date.getDate())) {
                    // Disable selection for decorated dates
                    calendarView.clearSelection();
                    LocalDate selectedDate = LocalDate.of(date.getYear(), date.getMonth(), date.getDay());
                    // Set up event decorators
                    for (DocumentReference sessionRef : bookedSessionsRefs) {
                        // Handle failure to fetch color from Firestore
                        sessionRef.get().addOnSuccessListener(sessionDocumentSnapshot -> {
                            if (sessionDocumentSnapshot.exists()) {
                                DocumentReference sessionPackageRef = sessionDocumentSnapshot.getDocumentReference("sessionPackageRef");
                                if (sessionPackageRef != null) {
                                    sessionPackageRef.get().addOnSuccessListener(sessionPackageDocumentSnapshot -> {
                                        if (sessionPackageDocumentSnapshot.exists()) {
                                            // Fetch tourPackageName from the referenced sessionPackageRef document
                                            LocalDate startDate, endDate;
                                            if (sessionDocumentSnapshot.contains("startDate")) {
                                                startDate = getLocalDate(sessionDocumentSnapshot.getString("startDate"));
                                                if(sessionDocumentSnapshot.contains("endDate"))
                                                    endDate = getLocalDate(sessionDocumentSnapshot.getString("endDate"));
                                                else
                                                    endDate = startDate;
                                                if (selectedDate.equals(startDate) || selectedDate.equals(endDate) ||
                                                        (selectedDate.isAfter(startDate) && selectedDate.isBefore(endDate))) {
                                                    if(isAdded()) {
                                                        // Create the dialog
                                                        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
                                                        View dialogView = getLayoutInflater().inflate(R.layout.dialog_view_session_details, null);
                                                        builder.setView(dialogView);
                                                        AlertDialog dialog = builder.create();

                                                        TextView sessionDate = dialogView.findViewById(R.id.sessionDate);
                                                        TextView sessionPackage = dialogView.findViewById(R.id.sessionPackage);
                                                        TextView groupSize = dialogView.findViewById(R.id.groupSize);
                                                        TextView bookedUsersAmount = dialogView.findViewById(R.id.bookedUsersAmount);


                                                        sessionPackageRef.getParent().getParent().get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                                            @Override
                                                            public void onSuccess(DocumentSnapshot documentSnapshot) {
                                                                if (documentSnapshot.exists()) { // Check if the query snapshot is not empty
                                                                    // Assuming there's only one document in the result (parent document)

                                                                    String freeDiverName = documentSnapshot.getString("username");

                                                                    if (freeDiverName != null) {
                                                                        ConstraintLayout userOnlyLayout = dialogView.findViewById(R.id.userOnlyLayout);
                                                                        userOnlyLayout.setVisibility(View.VISIBLE);
                                                                        TextView freeDiver = dialogView.findViewById(R.id.freeDiver);
                                                                        freeDiver.setText(freeDiverName);

                                                                        userOnlyLayout.setOnClickListener(new View.OnClickListener() {
                                                                            @Override
                                                                            public void onClick(View v) {
                                                                                dialog.dismiss();
                                                                                if(!Objects.equals(thisUserId, documentSnapshot.getId())){
                                                                                    Bundle args = new Bundle();
                                                                                    args.putString("userId", documentSnapshot.getId());
                                                                                    OtherProfileFragment otherProfileFragment = new OtherProfileFragment();
                                                                                    otherProfileFragment.setArguments(args);
                                                                                    AppUtils.switchFragment(OtherProfileFragment.this, otherProfileFragment);
                                                                                }
                                                                                else
                                                                                    AppUtils.switchFragment(OtherProfileFragment.this, new ThisProfileFragment());
                                                                            }
                                                                        });
                                                                    } else {
                                                                        // Handle case where username field is not found or null
                                                                        Log.e("DATABASE", "Username field is null or not found in parent document");
                                                                    }
                                                                } else {
                                                                    // Handle case where no parent document is found
                                                                    Log.e("DATABASE", "No parent document found for sessionPackageRef");
                                                                }
                                                            }

                                                        });


                                                        // Retrieve data from Firestore
                                                        int groupSizeInt = sessionDocumentSnapshot.getLong("groupSize").intValue();
                                                        int bookedUsersInt = sessionDocumentSnapshot.getLong("bookedUsersAmount").intValue();

                                                        // Populate AlertDialog
                                                        if (endDate != startDate)
                                                            sessionDate.setText(startDate.toString() + requireContext().getResources().getString(R.string.to) + endDate.toString());
                                                        else
                                                            sessionDate.setText(startDate.toString());

                                                        // Set the tour package name in the dialog
                                                        sessionPackage.setText(sessionPackageRef.getId()); // Set the tour package name

                                                        DocumentReference userDocument = sessionPackageRef.getParent().getParent();

                                                        groupSize.setText(" / " + groupSizeInt);
                                                        int availableSpots = groupSizeInt - bookedUsersInt;
                                                        bookedUsersAmount.setText(String.valueOf(availableSpots));

                                                        // Calculate the percentage of spots left relative to group size
                                                        double percentage = (double) availableSpots / groupSizeInt * 100;

                                                        // Set the color of the text based on the percentage
                                                        if (availableSpots == 0) {
                                                            bookedUsersAmount.setTextColor(getContext().getColor(R.color.red));
                                                        } else if (percentage < 25) {
                                                            bookedUsersAmount.setTextColor(getContext().getColor(R.color.orange_primary)); // Orange color
                                                        } else if (percentage < 50) {
                                                            bookedUsersAmount.setTextColor(getContext().getColor(R.color.yellow));
                                                        }

                                                        userDocument.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                                            @Override
                                                            public void onSuccess(DocumentSnapshot documentSnapshot) {
                                                                // Show the dialog
                                                                dialog.show();

                                                            }
                                                        }).addOnFailureListener(new OnFailureListener() {
                                                            @Override
                                                            public void onFailure(@NonNull Exception e) {
                                                                Log.e("DATABASE", "Error getting sessionPackageRef parent document", e);
                                                            }
                                                        });

                                                    }
                                                }
                                            }

                                        }
                                    }).addOnFailureListener(e -> {
                                        // Handle failure to fetch sessionPackageRef document
                                        Log.e("DATABASE", "Error getting sessionPackageRef document", e);
                                    });
                                } else {
                                    Log.e("DATABASE", "sessionPackageRef is null");
                                }
                            }
                        }).addOnFailureListener(e -> {
                            // Handle failure to fetch sessionRef document
                            Log.e("DATABASE", "Error getting sessionRef document", e);
                        });

                    }

                }
            }
        });
    }
    private void freeDiver_sessionBookingManager(){
        calendarView.setOnDateChangedListener(new OnDateSelectedListener() {
            @Override
            public void onDateSelected(@NonNull MaterialCalendarView widget, @NonNull CalendarDay date, boolean selected) {
                calendarView.clearSelection();
                if (decoratedDatesList.contains(date.getDate())) {
                    LocalDate selectedDate = LocalDate.of(date.getYear(), date.getMonth(), date.getDay());
                    // Iterate through active tours to find the corresponding tour for the selected date
                    for (ActiveSession session : activeSessions) {
                        LocalDate startDate = getLocalDate(session.getStartDate());
                        LocalDate endDate;
                        if(session.getEndDate().isEmpty())
                            endDate = startDate;
                        else
                            endDate = getLocalDate(session.getEndDate());

                        // Check if the selected date is between the start and end dates of the session
                        if (selectedDate.equals(startDate) || selectedDate.equals(endDate) ||
                                (selectedDate.isAfter(startDate) && selectedDate.isBefore(endDate))){
                            DocumentReference activesSessionRef = db.collection("users")
                                    .document(otherUserId)
                                    .collection("activeSessions")
                                    .document(session.getStartDate());

                            activesSessionRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                @Override
                                public void onSuccess(DocumentSnapshot activeSessionDocumentSnapshot) {
                                    if (activeSessionDocumentSnapshot.exists()) {
                                        if(isAdded()) {
                                            // Retrieve data from Firestore
                                            // Reference to the Firestore document
                                            DocumentReference thisUserDocumentRef = db.collection("users").document(thisUserId);

                                            // Retrieve data from Firestore
                                            thisUserDocumentRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                                @Override
                                                public void onSuccess(DocumentSnapshot thisUserdocumentSnapshot) {
                                                    if (isAdded() && thisUserdocumentSnapshot.exists()) {
                                                        // Check if the user type exists in the Firestore document
                                                        if (thisUserdocumentSnapshot.contains("type"))
                                                            thisUserType = thisUserdocumentSnapshot.getString("type");

                                                        // Create the dialog
                                                        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
                                                        View dialogView = getLayoutInflater().inflate(R.layout.dialog_view_session_details, null);
                                                        builder.setView(dialogView);
                                                        AlertDialog dialog = builder.create();

                                                        TextView sessionDate = dialogView.findViewById(R.id.sessionDate);
                                                        TextView sessionPackage = dialogView.findViewById(R.id.sessionPackage);
                                                        TextView groupSize = dialogView.findViewById(R.id.groupSize);
                                                        TextView bookedUsersAmount = dialogView.findViewById(R.id.bookedUsersAmount);
                                                        Button btnBookSession = dialogView.findViewById(R.id.btnBookSession);
                                                        btnBookSession.setVisibility(View.VISIBLE);


                                                        String sessionPackageName = session.getSessionPackageRef().getId();
                                                        String startDate = session.getStartDate();
                                                        String endDate = session.getEndDate();

                                                        int groupSizeInt = activeSessionDocumentSnapshot.getLong("groupSize").intValue();
                                                        int bookedUsersInt = activeSessionDocumentSnapshot.getLong("bookedUsersAmount").intValue();
                                                        // Populate AlertDialog

                                                        if (endDate != null)
                                                            sessionDate.setText(startDate + requireContext().getResources().getString(R.string.to) + endDate);
                                                        else
                                                            sessionDate.setText(startDate);

                                                        sessionPackage.setText(sessionPackageName);
                                                        groupSize.setText(" / " + groupSizeInt);
                                                        int availableSpots = groupSizeInt - bookedUsersInt;
                                                        bookedUsersAmount.setText(String.valueOf(availableSpots));

                                                        // Calculate the percentage of spots left relative to group size
                                                        double percentage = (double) availableSpots / groupSizeInt * 100;

                                                        // Set the color of the text based on the percentage
                                                        if (availableSpots == 0)
                                                            bookedUsersAmount.setTextColor(getContext().getColor(R.color.red));
                                                        else if (percentage < 25)
                                                            bookedUsersAmount.setTextColor(getContext().getColor(R.color.orange_primary)); // Orange color
                                                        else if (percentage < 50)
                                                            bookedUsersAmount.setTextColor(getContext().getColor(R.color.yellow));

                                                        if(Objects.equals(thisUserType, "Free Diver"))
                                                            btnBookSession.setVisibility(View.GONE);

                                                        else if (availableSpots == 0)
                                                            btnBookSession.setEnabled(false);




                                                        btnBookSession.setOnClickListener(new View.OnClickListener() {
                                                            @Override
                                                            public void onClick(View v) {
                                                                // Reference to the booked tours of the current user
                                                                CollectionReference bookedSessionsRef = db.collection("users")
                                                                        .document(thisUserId).collection("bookedSessions");
                                                                final boolean[] isOverlap = {false};
                                                                bookedSessionsRef.get().addOnSuccessListener(queryDocumentSnapshots -> {
                                                                    final AtomicInteger completedQueries = new AtomicInteger(0);
                                                                    final int totalQueries = queryDocumentSnapshots.size();
                                                                    for (QueryDocumentSnapshot bookedSession : queryDocumentSnapshots) {
                                                                        String bookedStartDate = bookedSession.getId();
                                                                        final String[] bookedEndDate = {null};

                                                                        DocumentReference sessionRef = bookedSession.getDocumentReference("sessionRef");

                                                                        sessionRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                                                            @Override
                                                                            public void onSuccess(DocumentSnapshot sessionDocumentSnapshot) {
                                                                                if (isAdded() && sessionDocumentSnapshot.exists()) {
                                                                                    // Check if booked session has an end date
                                                                                    if (sessionDocumentSnapshot.contains("endDate")) {
                                                                                        bookedEndDate[0] = sessionDocumentSnapshot.getString("endDate");
                                                                                    }
                                                                                }
                                                                            }
                                                                        }).addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                                                            @Override
                                                                            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                                                                // Check for overlap
                                                                                if (checkOverlappingSessions(session.getStartDate(), session.getEndDate(), bookedStartDate, bookedEndDate[0])) {
                                                                                    // A session is already booked within the given date range
                                                                                    Toast.makeText(requireContext(), requireContext().getResources().getString(R.string.you_have_already_booked_a_session_during_this_period), Toast.LENGTH_SHORT).show();
                                                                                    isOverlap[0] = true;
                                                                                }
                                                                                completedQueries.incrementAndGet();
                                                                                if (completedQueries.get() == totalQueries && !isOverlap[0]) {
                                                                                    // No overlapping session found, proceed to book the session
                                                                                    bookSession(session.getStartDate(), activeSessionDocumentSnapshot);
                                                                                    dialog.dismiss();
                                                                                }
                                                                            }
                                                                        });
                                                                    }
                                                                    if (totalQueries == 0 || completedQueries.get() == totalQueries && !isOverlap[0]) {
                                                                        // No booked tours found or all queries completed with no overlap
                                                                        bookSession(session.getStartDate(), activeSessionDocumentSnapshot);
                                                                        dialog.dismiss();
                                                                    }
                                                                }).addOnFailureListener(e -> {
                                                                    // Handle failure
                                                                    Log.e("DATABASE", "Error checking booked sessions", e);
                                                                });
                                                            }
                                                        });


                                                        // Show the dialog
                                                        dialog.show();
                                                    }

                                                }
                                            }).addOnFailureListener(new OnFailureListener() {
                                                @Override
                                                public void onFailure(@NonNull Exception e) {
                                                    Log.e("DATABASE", "Error getting document", e);
                                                }
                                            });
                                        }
                                    } else
                                        Log.d("DATABASE", "No such document");

                                }
                            }).addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    Log.d("DATABASE", "Error getting document", e);
                                }
                            });
                        }

                    }
                }
            }
        });
    }


    // Method to check overlap between two date ranges
    private boolean checkOverlappingSessions(String newStartDate, String newEndDate, String existingStartDate, String existingEndDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.ENGLISH);
            Date newStart = sdf.parse(newStartDate);
            Date newEnd = sdf.parse(newEndDate);
            Date existingStart = sdf.parse(existingStartDate);
            Date existingEnd = existingEndDate != null ? sdf.parse(existingEndDate) : null;

            // Check if any day between the start and end dates of the new tour falls within existing tour's date range
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(newStart);
            while (!calendar.getTime().after(newEnd)) {
                Date currentDate = calendar.getTime();
                if ((existingStart == null || !currentDate.before(existingStart)) && (existingEnd == null || !currentDate.after(existingEnd)))
                    return true; // Overlap found

                calendar.add(Calendar.DAY_OF_MONTH, 1); // Move to the next day
            }

            return false; // No overlap found
        } catch (ParseException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void bookSession(String startDate, DocumentSnapshot sessionRef) {
        // Create a new booked tour document
        Map<String, Object> bookedSessionData = new HashMap<>();
        bookedSessionData.put("sessionRef", sessionRef.getReference()); // Use getData() to retrieve all data from DocumentSnapshot

        // Add the booked tour to the user's collection of booked tours
        db.collection("users").document(thisUserId).collection("bookedSessions").document(startDate)
                .set(bookedSessionData)
                .addOnSuccessListener(aVoid -> {
                    // Tour successfully booked
                    Toast.makeText(requireContext(), requireContext().getResources().getString(R.string.session_booked_successfully), Toast.LENGTH_SHORT).show();

                    // Increment the bookedUsersAmount in the session document
                    DocumentReference sessionDocRef = sessionRef.getReference();
                    sessionDocRef.update("bookedUsersAmount", FieldValue.increment(1))
                            .addOnFailureListener(e -> Log.e("DATABASE", "Error incrementing bookedUsersAmount", e));

                    // Fetch the current user's email
                    db.collection("users").document(thisUserId).get()
                            .addOnSuccessListener(userDoc -> {
                                if (userDoc.exists()) {
                                    String userEmail = userDoc.getString("email");
                                    if (userEmail != null) {
                                        Log.d("BOOK_SESSION", "Current user's email: " + userEmail);

                                        // Optionally, send the email to a remote service or use it as needed
                                        sendEmailToService(userEmail, startDate, sessionRef.getId());
                                    } else {
                                        Log.e("BOOK_SESSION", "Email not found in user document");
                                    }
                                } else {
                                    Log.e("BOOK_SESSION", "User document does not exist");
                                }
                            })
                            .addOnFailureListener(e -> Log.e("DATABASE", "Error fetching user email", e));

                    dataCache.clearCache();
                })
                .addOnFailureListener(e -> {
                    // Handle failure
                    Log.e("DATABASE", "Error booking session", e);
                });
    }

    private void sendEmailToService(String email, String startDate, String sessionId) {
        Log.d("SEND_EMAIL", "Sending email via MailerSend: Email=" + email + ", SessionID=" + sessionId + ", StartDate=" + startDate);

        String apiKey = "mlsn.6c848d16e54d26e454865d47c96453588634673ed5859151a6647cde196188f8";
        String mailerSendUrl = "https://api.mailersend.com/v1/email";

        // Email details
        String subject = "Session Booking Confirmation";
        String message = "Dear User,\n\nYou have successfully booked a session.\n\n" +
                "Session Details:\n" +
                "Session ID: " + sessionId + "\n" +
                "Start Date: " + startDate + "\n\n" +
                "Attached here is the Drive link for your exclusive Pre-session Materials.\n" +
                "Please do not disseminate this. Happy reading, we can't wait to dive with you!\n" +
                "https://drive.google.com/drive/folders/1dAMzf1d4b-sUNW7AJLbKFSgp7dIOsRgJ?usp=sharing.\n" +
                "Thank you for booking with us.\n";

        // Create JSON payload
        JSONObject jsonPayload = new JSONObject();
        try {
            jsonPayload.put("from", new JSONObject().put("email", "no-reply@trial-3yxj6ljr9p04do2r.mlsender.net").put("name", "seaseed"));
            jsonPayload.put("to", new JSONArray().put(new JSONObject().put("email", email)));
            jsonPayload.put("subject", subject);
            jsonPayload.put("text", message);
        } catch (Exception e) {
            Log.e("SEND_EMAIL", "Error creating JSON payload", e);
            return;
        }

        // OkHttp client
        OkHttpClient client = new OkHttpClient();

        // Build the request
        RequestBody body = RequestBody.create(
                jsonPayload.toString(),
                MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(mailerSendUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        // Execute the request
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.d("SEND_EMAIL", "Email sent successfully: " + response.body().string());
                } else {
                    Log.e("SEND_EMAIL", "Failed to send email. Response Code: " + response.code());
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("SEND_EMAIL", "Error sending email", e);
            }
        });
    }





    void setEventDecorator(String startDate, String endDate, int colorCode) {
        if(isAdded()){
            LocalDate startLocalDate = getLocalDate(startDate);
            LocalDate endLocalDate = getLocalDate(endDate);

            if (startLocalDate == null)
                return;

            if(endLocalDate == null){
                Drawable dateDecoratorIndependent = createColoredDrawable(colorCode, R.drawable.date_decorator_independent);
                setDecor(Collections.singletonList(CalendarDay.from(startLocalDate)), dateDecoratorIndependent);
                decoratedDatesList.add(startLocalDate);
                return;
            }

            // Add start, end, and middle dates to the decorated dates list
            LocalDate currentDate = startLocalDate.plusDays(1); // Start with the day after the start date
            List<CalendarDay> centerDates = new ArrayList<>();

            while (currentDate.isBefore(endLocalDate)) {
                decoratedDatesList.add(currentDate);
                centerDates.add(CalendarDay.from(currentDate));
                currentDate = currentDate.plusDays(1);
            }
            decoratedDatesList.add(startLocalDate);
            decoratedDatesList.add(endLocalDate);

            // Create decorators for start, end, and middle dates
            Drawable dateDecoratorStart  = createColoredDrawable(colorCode, R.drawable.date_decorator_start);
            Drawable dateDecoratorEnd = createColoredDrawable(colorCode, R.drawable.date_decorator_end);
            Drawable dateDecoratorCenter  = createColoredDrawable(colorCode, R.drawable.date_decorator_center);


            // Add decorators to the MaterialCalendarView
            if (requireContext().getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                setDecor(Collections.singletonList(CalendarDay.from(startLocalDate)), dateDecoratorStart);
                setDecor(Collections.singletonList(CalendarDay.from(endLocalDate)), dateDecoratorEnd);
            } else {
                setDecor(Collections.singletonList(CalendarDay.from(endLocalDate)), dateDecoratorStart);
                setDecor(Collections.singletonList(CalendarDay.from(startLocalDate)), dateDecoratorEnd);
            }
            setDecor(centerDates, dateDecoratorCenter);
        }
    }

    Drawable createColoredDrawable(int colorCode, int drawableResId) {
        if(isAdded()) {
            Drawable originalDrawable = ContextCompat.getDrawable(requireContext(), drawableResId);
            Drawable.ConstantState constantState = originalDrawable.getConstantState();
            if (constantState != null) {
                Drawable drawable = constantState.newDrawable().mutate();
                drawable.setTint(colorCode);
                return drawable;
            } else
                return null;
        }
        return null;
    }

    void setDecor(List<CalendarDay> calendarDayList, Drawable drawable) {
        calendarView.addDecorators(new EventDecorator(drawable, calendarDayList));
    }

    LocalDate getLocalDate(String date) {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.ENGLISH);
        try {
            Date input = sdf.parse(date);
            Calendar cal = Calendar.getInstance();
            cal.setTime(Objects.requireNonNull(input));
            return LocalDate.of(cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH));
        } catch (ParseException | NullPointerException e) {
            return null;
        }
    }



    /**
     * Fetch user data from Firebase Firestore.
     */
    private void loadDataFromFirebase(View view) {
        // Initialize RecyclerView for Tour Packages

        if (otherUserId != null) {
            sessionPackageRecyclerViewAdapter = new SessionPackageRecyclerViewAdapter(getActivity(), otherUserId, sessionPackagesIdList, false, this);
            recyclerViewSessions.setAdapter(sessionPackageRecyclerViewAdapter);
            recyclerViewSessions.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));


            // Reference to the Firestore document
            DocumentReference userDocumentRef = db.collection("users").document(otherUserId);

            // Retrieve data from Firestore
            userDocumentRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                @Override
                public void onSuccess(DocumentSnapshot documentSnapshot) {
                    if (isAdded() && documentSnapshot.exists()) {
                        // Check if the user fields exists in the Firestore document
                        if (documentSnapshot.contains("username"))
                            username.setText(documentSnapshot.getString("username"));

                        if (documentSnapshot.contains("bio"))
                            if(!Objects.equals(documentSnapshot.getString("bio"), "")){
                                bio.setText(documentSnapshot.getString("bio"));
                                bio.setVisibility(View.VISIBLE);
                            }

                        if (documentSnapshot.contains("birthDate"))
                            birthDate.setText(documentSnapshot.getString("birthDate"));

                        if (documentSnapshot.contains("gender")) {
                            String[] genderOptions = requireContext().getResources().getStringArray(R.array.genders);
                            if (Objects.equals(documentSnapshot.getString("gender"), "Male"))
                                gender.setText(genderOptions[1]);
                            else if (Objects.equals(documentSnapshot.getString("gender"), "Female"))
                                gender.setText(genderOptions[2]);
                            else
                                gender.setText(genderOptions[3]);
                        }

                        if (documentSnapshot.contains("type")) {
                            otherUserType = documentSnapshot.getString("type");
                            if (Objects.equals(documentSnapshot.getString("type"), "User")) {
                                type.setText(requireContext().getResources().getString(R.string.user));

                            }
                            else {
                                type.setText(requireContext().getResources().getString(R.string.free_diver));
                            }
                        }

                        // Load the image into the ImageView using Glide
                        storageReference.child("images/" + otherUserId + "/profilePic").getDownloadUrl()
                                .addOnSuccessListener(new OnSuccessListener<Uri>() {
                                    @Override
                                    public void onSuccess(Uri uri) {
                                        // Got the download URL for 'users/me/profile.png'
                                        Glide.with(view)
                                                .load(uri)
                                                .apply(RequestOptions.circleCropTransform())
                                                .into(profilePic);
                                        userDataLoadingOverlay.setVisibility(View.GONE);
                                    }
                                }).addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception exception) {
                                        // Handle failure to load image
                                        userDataLoadingOverlay.setVisibility(View.GONE);
                                    }
                                });

                        if(Objects.equals(otherUserType, "Free Diver")) {
                            // Get the reference to the "tourPackages" collection
                            CollectionReference sessionPackagesRef = userDocumentRef.collection("sessionPackages");

                            // Retrieve documents from the "tourPackages" collection
                            sessionPackagesRef.get().addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                                @Override
                                public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                                    for (QueryDocumentSnapshot document : queryDocumentSnapshots)
                                        sessionPackagesIdList.add(document.getId());
                                    if(sessionPackagesIdList.isEmpty())
                                        noAvailableSessionPackages.setVisibility(View.VISIBLE);
                                    sessionPackageRecyclerViewAdapter.notifyDataSetChanged();
                                }
                            }).addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    Log.e("DATABASE", "Error getting sessionPackages collection", e);
                                }
                            });

                            activeSessions = new ArrayList<>();
                            db.collection("users")
                                    .document(otherUserId)
                                    .collection("activeSessions")
                                    .get()
                                    .addOnSuccessListener(queryDocumentSnapshots -> {
                                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                                            String endDate = "", startDate = "";
                                            DocumentReference sessionPackageRef = null;

                                            // Assuming ActiveTour is a model class representing the active tours
                                            if(document.contains("endDate"))
                                                endDate = document.get("endDate").toString();

                                            if(document.contains("startDate"))
                                                startDate = document.get("startDate").toString();

                                            if(document.contains("sessionPackageRef"))
                                                sessionPackageRef = document.getDocumentReference("sessionPackageRef");

                                            ActiveSession activeSession = new ActiveSession(startDate, endDate, sessionPackageRef);
                                            activeSessions.add(activeSession);
                                        }

                                        int index = 0; // Initialize an index counter before the loop
                                        int totalSessions = activeSessions.size(); // Get the total number of tours
                                        if(totalSessions == 0)
                                            calendarLoadingOverlay.setVisibility(View.GONE);

                                        // Set up event decorators
                                        for (ActiveSession session : activeSessions) {
                                            final int currentIndex = index++; // Use a final variable to use inside the lambda expression
                                            if (session.getSessionPackageRef() != null) {
                                                // Handle failure to fetch color from Firestore
                                                session.getSessionPackageRef().get().addOnSuccessListener(sessionPackageDocumentSnapshot -> {
                                                    if (sessionPackageDocumentSnapshot.exists()) {
                                                        int packageColor;
                                                        // Assuming color is stored as an integer in the 'color' field
                                                        if (sessionPackageDocumentSnapshot.contains("packageColor")) {
                                                            packageColor = sessionPackageDocumentSnapshot.getLong("packageColor").intValue();
                                                            setEventDecorator(session.getStartDate(), session.getEndDate(), packageColor);

                                                        }
                                                        else {
                                                            packageColor = requireContext().getResources().getColor(R.color.orange_primary);
                                                            setEventDecorator(session.getStartDate(), session.getEndDate(), packageColor);
                                                        }
                                                        // New block to check if this is the last session
                                                        if (currentIndex == totalSessions - 1)
                                                            // This is the last session, so remove the loading overlay
                                                            calendarLoadingOverlay.setVisibility(View.GONE);
                                                        session.setColor(packageColor);
                                                    }
                                                }).addOnFailureListener(Throwable::printStackTrace);
                                            }
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("DATABASE", "Error getting active sessions", e);
                                    });

                        }

                        else {
                            bookedSessionsRefs = new ArrayList<>();
                            db.collection("users")
                                    .document(otherUserId)
                                    .collection("bookedSessions")
                                    .get()
                                    .addOnSuccessListener(queryDocumentSnapshots -> {
                                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                                            DocumentReference sessionRef = document.getDocumentReference("sessionRef");
                                            bookedSessionsRefs.add(sessionRef);
                                        }
                                        int index = 0; // Initialize an index counter before the loop
                                        int totalSessions = bookedSessionsRefs.size(); // Get the total number of tours
                                        if(totalSessions == 0)
                                            calendarLoadingOverlay.setVisibility(View.GONE);

                                        // Set up event decorators
                                        for (DocumentReference sessionRef : bookedSessionsRefs) {
                                            final int currentIndex = index++; // Use a final variable to use inside the lambda expression
                                            // Handle failure to fetch color from Firestore
                                            sessionRef.get().addOnSuccessListener(sessionDocumentSnapshot -> {
                                                if (sessionDocumentSnapshot.exists()) {
                                                    DocumentReference sessionPackageRef = sessionDocumentSnapshot.getDocumentReference("sessionPackageRef");
                                                    if (sessionPackageRef != null) {
                                                        sessionPackageRef.get().addOnSuccessListener(sessionPackageDocumentSnapshot -> {
                                                            if (sessionPackageDocumentSnapshot.exists()) {
                                                                int packageColor = requireContext().getResources().getColor(R.color.orange_primary);
                                                                String startDate, endDate;
                                                                // Assuming color is stored as an integer in the 'packageColor' field
                                                                if (sessionPackageDocumentSnapshot.contains("packageColor")) {
                                                                    packageColor = sessionPackageDocumentSnapshot.getLong("packageColor").intValue();
                                                                }

                                                                if (sessionDocumentSnapshot.contains("startDate")) {
                                                                    startDate = sessionDocumentSnapshot.getString("startDate");
                                                                    if(sessionDocumentSnapshot.contains("endDate"))
                                                                        endDate = sessionDocumentSnapshot.getString("endDate");
                                                                    else
                                                                        endDate = "";

                                                                    if(Objects.equals(endDate, "")) {
                                                                        if (!getLocalDate(startDate).isBefore(min)) {
                                                                            setEventDecorator(startDate, endDate, packageColor);
                                                                            if (currentIndex == totalSessions - 1)
                                                                                // This is the last tour, so remove the loading overlay
                                                                                calendarLoadingOverlay.setVisibility(View.GONE);
                                                                        }
                                                                        else{
                                                                            if(isAdded()) {

                                                                                sessionRef.delete().addOnSuccessListener(aVoid -> {
                                                                                            // Refresh the fragment after deleting the tour reference
                                                                                            sessionPackagesIdList = new ArrayList<>();

                                                                                            // Remove all existing decorators
                                                                                            calendarView.removeDecorators();
                                                                                            // Clear the list of decorated dates
                                                                                            decoratedDatesList.clear();

                                                                                            loadDataFromFirebase(view);
                                                                                        })
                                                                                        .addOnFailureListener(e -> {
                                                                                            Log.e("DATABASE", "Error deleting sessionRef document", e);
                                                                                        });

                                                                                db.collection("users")
                                                                                        .document(otherUserId)
                                                                                        .collection("bookedSessions").document(sessionRef.getId())
                                                                                        .delete().addOnFailureListener(new OnFailureListener() {
                                                                                            @Override
                                                                                            public void onFailure(@NonNull Exception e) {
                                                                                                Log.e("DATABASE", "Error deleting session document", e);

                                                                                            }
                                                                                        });
                                                                            }

                                                                        }
                                                                    }
                                                                    else{
                                                                        if(!getLocalDate(endDate).isBefore(min)) {
                                                                            setEventDecorator(startDate, endDate, packageColor);
                                                                            if (currentIndex == totalSessions - 1)
                                                                                // This is the last tour, so remove the loading overlay
                                                                                calendarLoadingOverlay.setVisibility(View.GONE);
                                                                        }
                                                                        else {
                                                                            if(isAdded()) {

                                                                                sessionRef.delete().addOnSuccessListener(aVoid -> {
                                                                                            // Refresh the fragment after deleting the tour reference
                                                                                            sessionPackagesIdList = new ArrayList<>();

                                                                                            // Remove all existing decorators
                                                                                            calendarView.removeDecorators();
                                                                                            // Clear the list of decorated dates
                                                                                            decoratedDatesList.clear();

                                                                                            loadDataFromFirebase(view);
                                                                                        })
                                                                                        .addOnFailureListener(e -> {
                                                                                            Log.e("DATABASE", "Error deleting sessionRef document", e);
                                                                                        });

                                                                                db.collection("users")
                                                                                        .document(otherUserId)
                                                                                        .collection("bookedSessions").document(sessionRef.getId())
                                                                                        .delete().addOnFailureListener(new OnFailureListener() {
                                                                                            @Override
                                                                                            public void onFailure(@NonNull Exception e) {
                                                                                                Log.e("DATABASE", "Error deleting session document", e);

                                                                                            }
                                                                                        });
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }).addOnFailureListener(e -> {
                                                            // Handle failure to fetch sessionPackageRef document
                                                            Log.e("DATABASE", "Error getting sessionPackageRef document", e);
                                                        });
                                                    } else {
                                                        Log.e("DATABASE", "sessionPackageRef is null");
                                                    }
                                                }
                                                else{
                                                    if(isAdded()) {

                                                        sessionRef.delete().addOnSuccessListener(aVoid -> {
                                                                    // Refresh the fragment after deleting the tour reference
                                                                    sessionPackagesIdList = new ArrayList<>();

                                                                    // Remove all existing decorators
                                                                    calendarView.removeDecorators();
                                                                    // Clear the list of decorated dates
                                                                    decoratedDatesList.clear();

                                                                    loadDataFromFirebase(view);
                                                                })
                                                                .addOnFailureListener(e -> {
                                                                    Log.e("DATABASE", "Error deleting sessionRef document", e);
                                                                });

                                                        db.collection("users")
                                                                .document(otherUserId)
                                                                .collection("bookedSessions").document(sessionRef.getId())
                                                                .delete().addOnFailureListener(new OnFailureListener() {
                                                                    @Override
                                                                    public void onFailure(@NonNull Exception e) {
                                                                        Log.e("DATABASE", "Error deleting session document", e);

                                                                    }
                                                                });
                                                    }
                                                }
                                            }).addOnFailureListener(e -> {
                                                // Handle failure to fetch sessionRef document
                                                Log.e("DATABASE", "Error getting sessionRef document", e);
                                            });
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("DATABASE", "Error getting active sessions", e);
                                    });
                            // Save Active Tours to the data cache
                            dataCache.put("bookedSessions", bookedSessionsRefs);
                        }

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

    @Override
    public void onLoadComplete() {

    }

    public void enterChat(String userId1, String userId2, final OnChatCreatedListener listener) {
        // Check if userId1 is in any chat
        db.collection("chats")
                .whereArrayContains("participants", userId1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots1 -> {
                    // Check if userId2 is in any chat
                    db.collection("chats")
                            .whereArrayContains("participants", userId2)
                            .get()
                            .addOnSuccessListener(queryDocumentSnapshots2 -> {
                                // Check if userId1 and userId2 are both in the same chat
                                for (DocumentSnapshot doc1 : queryDocumentSnapshots1.getDocuments()) {
                                    String chatId1 = doc1.getId();
                                    for (DocumentSnapshot doc2 : queryDocumentSnapshots2.getDocuments()) {
                                        String chatId2 = doc2.getId();
                                        if (chatId1.equals(chatId2)) {
                                            // Chat already exists, retrieve the existing chat and notify listener
                                            Chat existingChat = doc1.toObject(Chat.class);
                                            db.collection("users").document(thisUserId).get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                                @Override
                                                public void onSuccess(DocumentSnapshot documentSnapshot) {
                                                    if(documentSnapshot.contains("username")){
                                                        existingChat.addParticipantName(thisUserId, documentSnapshot.getString("username"));
                                                        existingChat.addParticipantName(otherUserId, username.getText().toString());

                                                        listener.onChatExists(existingChat);
                                                    }
                                                }
                                            });

                                            return; // Exit method
                                        }
                                    }
                                }
                                // If userId1 and userId2 are not in the same chat, create a new chat
                                createNewChatDocument(userId1, userId2, listener);
                            })
                            .addOnFailureListener(e -> {
                                Log.e("DATABASE", "Error checking chat existence for userId2", e);
                                listener.onChatCreateFailed(e);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e("DATABASE", "Error checking chat existence for userId1", e);
                    listener.onChatCreateFailed(e);
                });
    }

    private void createNewChatDocument(String userId1, String userId2, final OnChatCreatedListener listener) {
        // Create a new chat document in Firestore
        Map<String, Object> chatData = new HashMap<>();
        List<String> participants = new ArrayList<>();
        participants.add(userId1);
        participants.add(userId2);
        Timestamp timeStamp = Timestamp.now();
        String lastMessage = "null";
        String lastMessageId = "null";

        chatData.put("participants", participants);
        chatData.put("timeStamp", timeStamp);
        chatData.put("lastMessage", lastMessage);
        chatData.put("lastMessageId", lastMessageId);

        db.collection("chats")
                .add(chatData)
                .addOnSuccessListener(documentReference -> {
                    String chatId = documentReference.getId();

                    // Retrieve the newly created chat document from Firestore
                    db.collection("chats").document(chatId)
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                // Get the data from the Firestore document
                                Chat chat = documentSnapshot.toObject(Chat.class);

                                // Notify listener about chat creation
                                listener.onChatCreated(chat);


                            })
                            .addOnFailureListener(e -> {
                                Log.e("DATABASE", "Error fetching chat document", e);
                                listener.onChatCreateFailed(e);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e("DATABASE", "Error creating chat", e);
                    listener.onChatCreateFailed(e);
                });
    }




    public interface OnChatCreatedListener {
        void onChatCreated(Chat chat);
        void onChatExists(Chat chat);
        void onChatCreateFailed(Exception e);



    }

}
