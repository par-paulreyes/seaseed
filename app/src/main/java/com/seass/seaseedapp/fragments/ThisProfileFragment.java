package com.seass.seaseedapp.fragments;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
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
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
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
import com.prolificinteractive.materialcalendarview.OnDateLongClickListener;
import com.prolificinteractive.materialcalendarview.OnDateSelectedListener;
import com.prolificinteractive.materialcalendarview.OnRangeSelectedListener;
import com.prolificinteractive.materialcalendarview.format.DateFormatTitleFormatter;
import com.seass.seaseedapp.R;
import com.seass.seaseedapp.adapters.ReviewRecyclerViewAdapter;
import com.seass.seaseedapp.adapters.SessionPackageRecyclerViewAdapter;
import com.seass.seaseedapp.decorators.EventDecorator;
import com.seass.seaseedapp.decorators.RangeSelectionDecorator;
import com.seass.seaseedapp.utils.ActiveSession;
import com.seass.seaseedapp.utils.DataCache;

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

import com.seass.seaseedapp.utils.AppUtils;

/**
 * Fragment for displaying user profile information.
 */
public class ThisProfileFragment extends Fragment implements SessionPackageRecyclerViewAdapter.OnLoadCompleteListener {
    // DataCache instance for caching user data
    DataCache dataCache;

    // UI elements
    ImageView profilePic;
    TextView username, bio, birthDate, gender, type,
            totalRating, noAvailableReviews, noAvailableSessionPackages,
            sessionPackagesORbookedSessionsHeadline, reviewsHeadline;;
    Button btnAddNewSession;
    com.chaek.android.RatingBar ratingBar;

    // Firebase
    FirebaseAuth mAuth;
    FirebaseUser mUser;
    FirebaseFirestore db;
    StorageReference storageReference;

    // SwipeRefreshLayout for pull-to-refresh functionality
    SwipeRefreshLayout swipeRefreshLayout;

    // Loading overlays
    RelativeLayout calendarLoadingOverlay, userDataLoadingOverlay;

    // RecyclerView for Tour Packages
    RecyclerView recyclerViewSessions, recyclerViewReviews;
    SessionPackageRecyclerViewAdapter sessionPackageRecyclerViewAdapter;
    ReviewRecyclerViewAdapter reviewsRecyclerViewAdapter;

    List<String> sessionPackagesIdList = new ArrayList<>();
    List<String> reviewsIdList = new ArrayList<>();

    String userId, userType;

    // Tour booking management
    String selectedStartDate = "", selectedEndDate = "";

    final LocalDate min = LocalDate.now(ZoneId.systemDefault()).plusDays(0);
    final LocalDate max = min.plusMonths(6);
    final String DATE_FORMAT = "yyyy-MM-dd";

    List<LocalDate> decoratedDatesList = new ArrayList<>(); // Define this globally
    List<ActiveSession> activeSessions;
    List<DocumentReference> bookedSessionsRefs;
    MaterialCalendarView calendarView;
    ConstraintLayout totalRatingLayout;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_this_profile, container, false);
        if(isAdded()) {
            // Initialize DataCache
            dataCache = DataCache.getInstance();

            // Initialize UI elements
            swipeRefreshLayout = view.findViewById(R.id.swipeRefresh);
            profilePic = view.findViewById(R.id.accountIcon);
            username = view.findViewById(R.id.username);
            bio = view.findViewById(R.id.bio);
            birthDate = view.findViewById(R.id.birthDate);
            gender = view.findViewById(R.id.gender);
            type = view.findViewById(R.id.type);
            btnAddNewSession = view.findViewById(R.id.btnAddNewSession);

            ratingBar = view.findViewById(R.id.ratingBar);
            totalRating = view.findViewById(R.id.totalRating);
            totalRatingLayout = view.findViewById(R.id.totalRatingLayout);

            noAvailableReviews = view.findViewById(R.id.noAvailableReviews);
            noAvailableSessionPackages = view.findViewById(R.id.noAvailableSessionPackages);

            sessionPackagesORbookedSessionsHeadline = view.findViewById(R.id.sessionPackagesORbookedSessionsHeadline);
            reviewsHeadline = view.findViewById(R.id.reviewsHeadline);

            // Initialize Firebase instances
            mAuth = FirebaseAuth.getInstance();
            mUser = mAuth.getCurrentUser();
            db = FirebaseFirestore.getInstance();
            FirebaseStorage storage = FirebaseStorage.getInstance();
            storageReference = storage.getReference();

            // Initialize loading overlay elements
            calendarLoadingOverlay = view.findViewById(R.id.calendarLoadingOverlay);
            userDataLoadingOverlay = view.findViewById(R.id.userDataLoadingOverlay);

            recyclerViewReviews = view.findViewById(R.id.recyclerViewReviews);
            recyclerViewSessions = view.findViewById(R.id.recyclerViewSessions);
            calendarView = view.findViewById(R.id.calendarView);

            mUser = mAuth.getCurrentUser();
            if (mUser != null)
                userId = mUser.getUid();

            // Initialize calendar and set its attributes
            calendarView.setShowOtherDates(MaterialCalendarView.SHOW_ALL);
            calendarView.state().edit().setMinimumDate(min).setMaximumDate(max).commit();

            // Attempt to load data from cache
            if (!loadDataFromCache(view)) {
                calendarLoadingOverlay.setVisibility(View.VISIBLE);
                userDataLoadingOverlay.setVisibility(View.VISIBLE);

                // Data not found in cache, fetch from the server
                loadDataFromFirebase(view);
            }

            DocumentReference userDocumentRef = db.collection("users").document(userId);

            // Retrieve userType from Firestore, duplicated from loadDataFromFirebase in case there's an asynchronous delay
            userDocumentRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                @Override
                public void onSuccess(DocumentSnapshot thisUserdocumentSnapshot) {
                    if (isAdded() && thisUserdocumentSnapshot.exists()) {
                        // Check if the user type exists in the Firestore document
                        if (thisUserdocumentSnapshot.contains("type")) {
                            userType = thisUserdocumentSnapshot.getString("type");
                            if (Objects.equals(userType, "Free Diver")) {
                                freeDiver_sessionBookingManager();
                                freeDiver_reviewsManager();
                                if (thisUserdocumentSnapshot.contains("language"))
                                     AppUtils.setAppLocale(requireContext(), thisUserdocumentSnapshot.getString("language"));
                            } else {
                                calendarView.setSelectionMode(MaterialCalendarView.SELECTION_MODE_SINGLE);
                                user_sessionBookingManager();
                                user_reviewsManager();

                            }
                            // Set the locale for the calendar view title formatter
                            calendarView.setTitleFormatter(new DateFormatTitleFormatter());
                        }
                    }
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
            } else
                Log.e("ThisProfileFragment", "SwipeRefreshLayout is null");
        }
        return view;

    }


    private void freeDiver_reviewsManager() {
        if(isAdded()) {
            CollectionReference collectionReference = db.collection("users").document(userId).collection("reviews");

            // Clear the list before adding new items
            reviewsIdList.clear();

            collectionReference.get().addOnSuccessListener(queryDocumentSnapshots -> {
                for (QueryDocumentSnapshot documentSnapshot : queryDocumentSnapshots)
                    // Add document ID to the reviewsIdList
                    reviewsIdList.add(documentSnapshot.getId());

                if (reviewsIdList != null && !reviewsIdList.isEmpty())
                    // Move the current user's review to the top if it exists
                    if (reviewsIdList.contains(userId)) {
                        reviewsIdList.remove(userId);
                        reviewsIdList.add(0, userId);
                    }

                collectionReference.getParent().get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        if (documentSnapshot.contains("totalRating")) {
                            if (!reviewsIdList.isEmpty()) {
                                float totalRatingValue = (float) documentSnapshot.getLong("totalRating") / (float) reviewsIdList.size();
                                totalRating.setText(String.format("%.1f", totalRatingValue / 2.0));
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

                if(isAdded()) {
                    // Initialize and set up the adapter after fetching the data
                    reviewsRecyclerViewAdapter = new ReviewRecyclerViewAdapter(getActivity(), requireContext(), reviewsIdList, userId, false);
                    recyclerViewReviews.setAdapter(reviewsRecyclerViewAdapter);
                    recyclerViewReviews.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
                }
            }).addOnFailureListener(e -> {
                // Handle failure
            });
        }
    }
    private void user_reviewsManager(){
        if(isAdded()) {
            CollectionReference collectionReference = db.collection("users").document(userId).collection("reviews");

            // Clear the list before adding new items
            reviewsIdList.clear();

            collectionReference.get().addOnSuccessListener(queryDocumentSnapshots -> {
                for (QueryDocumentSnapshot documentSnapshot : queryDocumentSnapshots)
                    // Add document ID to the reviewsIdList
                    reviewsIdList.add(documentSnapshot.getId());

                if (!reviewsIdList.isEmpty())
                    noAvailableReviews.setVisibility(View.GONE);
                else
                    noAvailableReviews.setVisibility(View.VISIBLE);

                // Notify the adapter if needed
                if (reviewsRecyclerViewAdapter != null) {
                    reviewsRecyclerViewAdapter.notifyDataSetChanged();
                }

                if(isAdded()) {
                    // Initialize and set up the adapter after fetching the data
                    reviewsRecyclerViewAdapter = new ReviewRecyclerViewAdapter(getActivity(), requireContext(), reviewsIdList, userId, true);
                    recyclerViewReviews.setAdapter(reviewsRecyclerViewAdapter);
                    recyclerViewReviews.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
                }
            }).addOnFailureListener(e -> {
                // Handle failure
            });
        }
    }

    private void user_sessionBookingManager(){
        btnAddNewSession.setVisibility(View.GONE);
        calendarView.setOnDateLongClickListener(new OnDateLongClickListener() {
            @Override
            public void onDateLongClick(@NonNull MaterialCalendarView widget, @NonNull CalendarDay date) {
                LocalDate selectedDate = LocalDate.of(date.getYear(), date.getMonth(), date.getDay());

                for (DocumentReference sessionRef : bookedSessionsRefs) {
                    // Handle failure to fetch color from Firestore
                    sessionRef.get().addOnSuccessListener(sessionDocumentSnapshot -> {
                        if (sessionDocumentSnapshot.exists()) {
                            // Fetch tourPackageName from the referenced tourPackageRef document
                            LocalDate startDate, endDate;
                            if (sessionDocumentSnapshot.contains("startDate")) {
                                startDate = getLocalDate(sessionDocumentSnapshot.getString("startDate"));
                                if (sessionDocumentSnapshot.contains("endDate"))
                                    endDate = getLocalDate(sessionDocumentSnapshot.getString("endDate"));
                                else
                                    endDate = startDate;
                                if (selectedDate.equals(startDate) || selectedDate.equals(endDate) ||
                                        (selectedDate.isAfter(startDate) && selectedDate.isBefore(endDate))) {
                                    showConfirmSessionCancellation(startDate.toString(), true);
                                }
                            }
                        }

                    }).addOnFailureListener(e -> {
                        // Handle failure to fetch sessionRef document
                        Log.e("DATABASE", "Error getting sessionRef document", e);
                    });
                }
            }
        });

        calendarView.setOnDateChangedListener(new OnDateSelectedListener() {
            @Override
            public void onDateSelected(@NonNull MaterialCalendarView widget, @NonNull CalendarDay date, boolean selected) {
                if (isAdded() && decoratedDatesList.contains(date.getDate())) {
                    // Disable selection for decorated dates
                    selectedStartDate = "";
                    selectedEndDate = "";
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

                                                        // Retrieve data from Firestore
                                                        int groupSizeInt = sessionDocumentSnapshot.getLong("groupSize").intValue();
                                                        int bookedUsersInt = sessionDocumentSnapshot.getLong("bookedUsersAmount").intValue();

                                                        // Populate AlertDialog
                                                        if (endDate != startDate)
                                                            sessionDate.setText(startDate.toString() + requireContext().getResources().getString(R.string.to) + endDate.toString());
                                                        else
                                                            sessionDate.setText(startDate.toString());

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
                                                                                if(!Objects.equals(userId, documentSnapshot.getId())){
                                                                                    Bundle args = new Bundle();
                                                                                    args.putString("userId", documentSnapshot.getId());
                                                                                    OtherProfileFragment otherProfileFragment = new OtherProfileFragment();
                                                                                    otherProfileFragment.setArguments(args);
                                                                                    AppUtils.switchFragment(ThisProfileFragment.this, otherProfileFragment);

                                                                                }
                                                                                else
                                                                                    AppUtils.switchFragment(ThisProfileFragment.this, new ThisProfileFragment());

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
                                                                Log.d("DATABASE", documentSnapshot.getString("username"));
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

                } else {
                    if (calendarView.getSelectedDate() != null) {
                        selectedStartDate = date.getDate().toString();
                        selectedEndDate = "";
                    } else {
                        selectedStartDate = "";
                        selectedEndDate = "";


                    }

                }
            }
        });
    }

    private void freeDiver_sessionBookingManager() {
        if (isAdded()) {
            // Instantiate the custom decorator with your custom drawable for selection
            Drawable selectedDrawable = requireContext().getResources().getDrawable(R.drawable.date_decorator_start);
            Drawable selectedDrawable2 = requireContext().getResources().getDrawable(R.drawable.date_decorator_center);
            Drawable selectedDrawable3 = requireContext().getResources().getDrawable(R.drawable.date_decorator_end);

            RangeSelectionDecorator rangeDecorator = new RangeSelectionDecorator(selectedDrawable, selectedDrawable2, selectedDrawable3);

            // Add the decorator to the MaterialCalendarView
            calendarView.addDecorator(rangeDecorator);


            // Set the date range selection listener
            calendarView.setOnRangeSelectedListener(new OnRangeSelectedListener() {
                @Override
                public void onRangeSelected(@NonNull MaterialCalendarView widget, @NonNull List<CalendarDay> dates) {
                    // Check if any of the selected dates are in the list of decorated dates
                    boolean disableSelection = dates.stream()
                            .map(CalendarDay::getDate)
                            .anyMatch(decoratedDatesList::contains);

                    // Check if any of the selected dates are in the list of decorated dates
                    if (disableSelection || sessionPackagesIdList.isEmpty()) {
                        // Disable range selection for decorated dates
                        btnAddNewSession.setEnabled(false);
                        selectedStartDate = "";
                        selectedEndDate = "";
                        calendarView.setSelectionMode(MaterialCalendarView.SELECTION_MODE_RANGE);
                        btnAddNewSession.setText(R.string.add_new_session);
                    } else {
                        // Clear the existing selection
                        rangeDecorator.clearSelection();

                        // Update the range decorator with the selected dates
                        rangeDecorator.setSelectedDates(dates);

                        // Invalidate the decorators to trigger a redraw
                        calendarView.invalidateDecorators();

                        calendarView.getSelectedDates();


                        selectedStartDate = dates.get(0).getDate().toString();
                        selectedEndDate = dates.get(dates.size() - 1).getDate().toString();

                        if (isAdded()) {
                            btnAddNewSession.setText(requireContext().getResources().getString(R.string.add_new_session) + "\n" + selectedStartDate + requireContext().getResources().getString(R.string.to) + selectedEndDate);
                            btnAddNewSession.setEnabled(true);
                        }


                    }
                }

            });

            calendarView.setOnDateLongClickListener(new OnDateLongClickListener() {
                @Override
                public void onDateLongClick(@NonNull MaterialCalendarView widget, @NonNull CalendarDay date) {
                    LocalDate selectedDate = LocalDate.of(date.getYear(), date.getMonth(), date.getDay());
                    // Iterate through active tours to find the corresponding tour for the selected date
                    for (ActiveSession session : activeSessions) {
                        LocalDate startDate = getLocalDate(session.getStartDate());
                        LocalDate endDate;
                        if (session.getEndDate().isEmpty())
                            endDate = startDate;
                        else
                            endDate = getLocalDate(session.getEndDate());

                        // Check if the selected date is between the start and end dates of the session
                        if (selectedDate.equals(startDate) || selectedDate.equals(endDate) ||
                                (selectedDate.isAfter(startDate) && selectedDate.isBefore(endDate)))
                            showConfirmSessionCancellation(startDate.toString(), true);
                    }
                }
            });


            calendarView.setOnDateChangedListener(new OnDateSelectedListener() {
                @Override
                public void onDateSelected(@NonNull MaterialCalendarView widget, @NonNull CalendarDay date, boolean selected) {
                    if (isAdded()) {
                        if (decoratedDatesList.contains(date.getDate())) {
                            // Disable selection for decorated dates
                            btnAddNewSession.setEnabled(false);
                            selectedStartDate = "";
                            selectedEndDate = "";
                            calendarView.setSelectionMode(MaterialCalendarView.SELECTION_MODE_RANGE);
                            btnAddNewSession.setText(R.string.add_new_session);

                            LocalDate selectedDate = LocalDate.of(date.getYear(), date.getMonth(), date.getDay());
                            // Iterate through active tours to find the corresponding tour for the selected date
                            for (ActiveSession session : activeSessions) {
                                LocalDate startDate = getLocalDate(session.getStartDate());
                                LocalDate endDate;
                                if (session.getEndDate().isEmpty())
                                    endDate = startDate;
                                else
                                    endDate = getLocalDate(session.getEndDate());

                                // Check if the selected date is between the start and end dates of the session
                                if (selectedDate.equals(startDate) || selectedDate.equals(endDate) ||
                                        (selectedDate.isAfter(startDate) && selectedDate.isBefore(endDate))) {
                                    // Print the session reference to the log
                                    Log.d("ActiveSessionReference", "Session reference: " + session.getSessionPackageRef());

                                    DocumentReference activeSessionRef = db.collection("users")
                                            .document(userId)
                                            .collection("activeSessions")
                                            .document(session.getStartDate());


                                    activeSessionRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                        @Override
                                        public void onSuccess(DocumentSnapshot documentSnapshot) {
                                            if (documentSnapshot.exists()) {
                                                if (isAdded()) {
                                                    // Create the dialog
                                                    AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
                                                    View dialogView = getLayoutInflater().inflate(R.layout.dialog_view_session_details, null);
                                                    builder.setView(dialogView);
                                                    AlertDialog dialog = builder.create();

                                                    TextView sessionDate = dialogView.findViewById(R.id.sessionDate);
                                                    TextView sessionPackage = dialogView.findViewById(R.id.sessionPackage);
                                                    TextView groupSize = dialogView.findViewById(R.id.groupSize);
                                                    TextView bookedUsersAmount = dialogView.findViewById(R.id.bookedUsersAmount);


                                                    String sessionPackageName = session.getSessionPackageRef().getId();
                                                    String startDate = session.getStartDate();
                                                    String endDate = session.getEndDate();

                                                    // Retrieve data from Firestore

                                                    int groupSizeInt = documentSnapshot.getLong("groupSize").intValue();
                                                    int bookedUsersInt = documentSnapshot.getLong("bookedUsersAmount").intValue();
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
                                                    if (availableSpots == 0) {
                                                        bookedUsersAmount.setTextColor(getContext().getColor(R.color.red));
                                                    } else if (percentage < 25) {
                                                        bookedUsersAmount.setTextColor(getContext().getColor(R.color.orange_primary)); // Orange color
                                                    } else if (percentage < 50) {
                                                        bookedUsersAmount.setTextColor(getContext().getColor(R.color.yellow));
                                                    }

                                                    // Show the dialog
                                                    dialog.show();
                                                }
                                            } else {
                                                Log.d("DATABASE", "No such document");
                                            }
                                        }
                                    }).addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            Log.d("DATABASE", "Error getting document", e);
                                        }
                                    });

                                }
                            }


                        } else {
                            if (calendarView.getSelectedDate() != null) {
                                if(sessionPackagesIdList.isEmpty()){
                                    btnAddNewSession.setEnabled(false);
                                    selectedStartDate = "";
                                    selectedEndDate = "";
                                    btnAddNewSession.setText(R.string.add_new_session);
                                }
                                selectedStartDate = date.getDate().toString();
                                selectedEndDate = "";
                                btnAddNewSession.setText(requireContext().getResources().getString(R.string.add_new_session) + "\n" + selectedStartDate);
                                btnAddNewSession.setEnabled(true);
                            } else {
                                btnAddNewSession.setEnabled(false);
                                selectedStartDate = "";
                                selectedEndDate = "";
                                btnAddNewSession.setText(R.string.add_new_session);


                            }

                        }
                    }
                }
            });

            btnAddNewSession.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LocalDate minStartDate = min.plusDays(4); // Minimum start date is 3 days from now
                    if (isAdded()) {
                        // Check if the selected start date is at least 3 days from the current date
                        if (LocalDate.parse(selectedStartDate).isBefore(minStartDate)) {
                            // Display an error message to the user
                            Toast.makeText(requireContext(), requireContext().getResources().getString(R.string.sessions_must_be_set_up_at_least_3_days_in_advance), Toast.LENGTH_SHORT).show();
                            return; // Prevent further execution of tour setup
                        }


                        // Create the dialog
                        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
                        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_new_session, null);
                        builder.setView(dialogView);
                        AlertDialog dialog = builder.create();

                        TextView sessionDate = dialogView.findViewById(R.id.sessionDate);
                        Button btnAddNewSession = dialogView.findViewById(R.id.btnAddNewSession);
                        EditText groupSize = dialogView.findViewById(R.id.groupSize);
                        Spinner spinner = dialogView.findViewById(R.id.sessionSpinner);


                        // Initialize spinner
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                                R.layout.spinner_custom_layout, sessionPackagesIdList);
                        adapter.setDropDownViewResource(R.layout.spinner_custom_dropdown_layout);
                        spinner.setAdapter(adapter);


                        if (!selectedEndDate.isEmpty())
                            sessionDate.setText(requireContext().getResources().getString(R.string.session_date) + " \n" + selectedStartDate + requireContext().getResources().getString(R.string.to) + selectedEndDate);
                        else
                            sessionDate.setText(requireContext().getResources().getString(R.string.session_date) + " " + selectedStartDate);


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
                                btnAddNewSession.setEnabled(AppUtils.isEditTextFilled(groupSize) && AppUtils.isPositiveNumeric(groupSize));
                            }
                        };

                        groupSize.addTextChangedListener(textWatcher);

                        btnAddNewSession.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                // Handle adding tour
                                String sessionPackageId = spinner.getSelectedItem().toString();
                                int groupSizeValue = Integer.parseInt(groupSize.getText().toString());
                                DocumentReference userSessionPackageDocumentRef = db.collection("users")
                                        .document(userId)
                                        .collection("sessionPackages")
                                        .document(sessionPackageId);

                                // Create a map to store user data
                                Map<String, Object> sessionData = new HashMap<>();
                                sessionData.put("sessionPackageRef", userSessionPackageDocumentRef);
                                sessionData.put("groupSize", groupSizeValue);
                                sessionData.put("bookedUsersAmount", 0);
                                sessionData.put("startDate", selectedStartDate);
                                if (!selectedEndDate.isEmpty())
                                    sessionData.put("endDate", selectedEndDate);

                                // Set the document name as the user ID
                                DocumentReference userActiveSessionDocumentRef = db.collection("users").
                                        document(userId)
                                        .collection("activeSessions")
                                        .document(selectedStartDate);

                                // Set the data to the Firestore document
                                userActiveSessionDocumentRef.set(sessionData).addOnSuccessListener(new OnSuccessListener<Void>() {
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

                                dataCache.clearCache();
                                AppUtils.switchFragment(ThisProfileFragment.this, new ThisProfileFragment());

                                // Close the dialog
                                dialog.dismiss();
                            }
                        });


                        // Show the dialog
                        dialog.show();
                    }
                }
            });
        }
    }

    private void showConfirmSessionCancellation(String startDate, boolean isCanceled) {
        LocalDate sessionStartDate = getLocalDate(startDate);

        // Check if the tour start date has passed
        if (isAdded() && sessionStartDate != null && sessionStartDate.isAfter(min.plusDays(3))) {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            builder.setTitle(requireContext().getResources().getString(R.string.confirmSessionCancellation));
            builder.setMessage(requireContext().getResources().getString(R.string.confirmSessionCancellationMessage));
            builder.setPositiveButton(requireContext().getResources().getString(R.string.cancel_session), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    cancelSession(startDate, isCanceled);
                }
            });

            builder.setNegativeButton(requireContext().getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // Do nothing, close the dialog
                }
            });

            builder.show();
        } else {
            if(isAdded())
                // Tour is in progress or already passed, display a message indicating cancellation is not allowed
                Toast.makeText(requireContext(), requireContext().getResources().getString(R.string.cancellation_is_not_allowed_for_ongoing_or_close_occurring_sessions), Toast.LENGTH_SHORT).show();
        }
    }

    private void cancelSession(String startDate, boolean isCanceled) {
        if(Objects.equals(userType, "Free Diver")) {
            // Query Firestore for documents with the selected date
            db.collection("users")
                    .document(userId)
                    .collection("activeSessions")
                    .whereEqualTo("startDate", startDate) // Assuming date is stored as a string
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        for (QueryDocumentSnapshot documentSnapshot : queryDocumentSnapshots) {
                            if (!isCanceled) {
                                // Get data from the active tour document
                                Map<String, Object> sessionData = documentSnapshot.getData();

                                // Create a reference to the document in the "completedTours" collection
                                db.collection("users")
                                        .document(userId)
                                        .collection("completedSessions")
                                        .document(documentSnapshot.getId()) // Use the same document ID
                                        .set(sessionData) // Copy data from active tour to completed tour
                                        .addOnSuccessListener(aVoid -> {
                                            // Document successfully copied to completedTours
                                            Log.d("DATABASE", "Active session copied to completedSessions");
                                        })
                                        .addOnFailureListener(e -> {
                                            // Handle errors
                                            Log.e("DATABASE", "Error copying active session to completedSessions", e);
                                        });

                            }
                            if(isAdded()) {
                                // Delete the document from the "activeTours" collection
                                db.collection("users")
                                        .document(userId)
                                        .collection("activeSessions")
                                        .document(documentSnapshot.getId())
                                        .delete()
                                        .addOnSuccessListener(aVoid -> {
                                            // Document successfully deleted from activeTours
                                            Log.d("DATABASE", "Active session successfully deleted!");
                                            dataCache.clearCache();
                                            AppUtils.switchFragment(ThisProfileFragment.this, new ThisProfileFragment());
                                        })
                                        .addOnFailureListener(e -> {
                                            // Handle errors
                                            Log.e("DATABASE", "Error deleting active session document", e);
                                        });
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        // Handle errors
                        Log.e("DATABASE", "Error getting active session documents", e);
                    });
        }
        else {
            // Query Firestore for documents with the selected date
            db.collection("users")
                    .document(userId)
                    .collection("bookedSessions")
                    .document(startDate) // Assuming date is stored as a string
                    .get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                        @Override
                        public void onSuccess(DocumentSnapshot documentSnapshot) {
                            if (!isCanceled) {
                                // Get data from the active tour document
                                Map<String, Object> sessionData = documentSnapshot.getData();

                                // Create a reference to the document in the "completedTours" collection
                                db.collection("users")
                                        .document(userId)
                                        .collection("completedSessions")
                                        .document(documentSnapshot.getId()) // Use the same document ID
                                        .set(sessionData) // Copy data from active tour to completed tour
                                        .addOnSuccessListener(aVoid -> {
                                            // Document successfully copied to completedTours
                                            Log.d("DATABASE", "Active session copied to completedSessions");
                                        })
                                        .addOnFailureListener(e -> {
                                            // Handle errors
                                            Log.e("DATABASE", "Error copying active session to completedSessions", e);
                                        });

                            }
                            if(isAdded()) {

                                // Delete the document from the "activeTours" collection
                                db.collection("users")
                                        .document(userId)
                                        .collection("bookedSessions")
                                        .document(documentSnapshot.getId())
                                        .delete()
                                        .addOnSuccessListener(aVoid -> {
                                            // Document successfully deleted from activeTours
                                            Log.d("DATABASE", "Booked session successfully deleted!");
                                            DocumentReference sessionDocRef = documentSnapshot.getDocumentReference("sessionRef");
                                            sessionDocRef.update("bookedUsersAmount", FieldValue.increment(-1))
                                                    .addOnFailureListener(e -> Log.e("DATABASE", "Error decreasing bookedUsersAmount", e));
                                            dataCache.clearCache();
                                            AppUtils.switchFragment(ThisProfileFragment.this, new ThisProfileFragment());
                                        })
                                        .addOnFailureListener(e -> {
                                            // Handle errors
                                            Log.e("DATABASE", "Error deleting active session document", e);
                                        });
                            }
                        }

                    }).addOnFailureListener(e -> {
                        // Handle errors
                        Log.e("DATABASE", "Error getting active session documents", e);
                    });
        }
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

        mUser = mAuth.getCurrentUser();
        if (isAdded() && mUser != null) {
            userId = mUser.getUid();
            // Reference to the Firestore document
            DocumentReference userDocumentRef = db.collection("users").document(userId);


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
                            if (Objects.equals(documentSnapshot.getString("type"), "User")){
                                type.setText(requireContext().getResources().getString(R.string.user));
                                userType = "User";
                            }
                            else {
                                type.setText(requireContext().getResources().getString(R.string.free_diver));
                                userType = "Free Diver";
                                // Initialize RecyclerView for Tour Packages
                                sessionPackageRecyclerViewAdapter = new SessionPackageRecyclerViewAdapter(getActivity(), userId, sessionPackagesIdList, true, ThisProfileFragment.this);
                                recyclerViewSessions.setAdapter(sessionPackageRecyclerViewAdapter);
                                recyclerViewSessions.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
                            }
                        }


                        // Load the image into the ImageView using Glide
                        storageReference.child("images/" + mUser.getUid() + "/profilePic").getDownloadUrl()
                                .addOnSuccessListener(new OnSuccessListener<Uri>() {
                                    @Override
                                    public void onSuccess(Uri uri) {
                                        // Got the download URL for 'users/me/profile.png'
                                        Glide.with(view)
                                                .load(uri)
                                                .apply(RequestOptions.circleCropTransform())
                                                .into(profilePic);
                                        userDataLoadingOverlay.setVisibility(View.GONE);
                                        saveDataToCache(uri); // Save profile picture URL);
                                    }
                                }).addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception exception) {
                                        // Handle failure to load image
                                        userDataLoadingOverlay.setVisibility(View.GONE);
                                        saveDataToCache(null); // Save profile picture URL);
                                    }
                                });

                        if(Objects.equals(userType, "Free Diver")){
                            // Get the reference to the "tourPackages" collection
                            CollectionReference sessionPackagesRef = userDocumentRef.collection("sessionPackages");

                            // Retrieve documents from the "tourPackages" collection
                            sessionPackagesRef.get().addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                                @Override
                                public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                                    for (QueryDocumentSnapshot document : queryDocumentSnapshots)
                                        sessionPackagesIdList.add(document.getId());
                                    sessionPackageRecyclerViewAdapter.notifyDataSetChanged();
                                }
                            }).addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    Log.e("DATABASE", "Error getting sessionPackages collection", e);
                                }
                            }).addOnCompleteListener(task -> calendarLoadingOverlay.setVisibility(View.GONE));

                            activeSessions = new ArrayList<>();
                            db.collection("users")
                                    .document(userId)
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

                                            // Cancel the tour if the end date has passed or if the start date has passed (when end date is empty)
                                            if (endDate != null && !endDate.isEmpty()) {
                                                LocalDate sessionEndDate = LocalDate.parse(endDate);
                                                if (sessionEndDate.isBefore(min)) {
                                                    // The end date of the tour has passed, cancel the tour
                                                    cancelSession(startDate, false);
                                                    continue; // Skip to the next tour
                                                }
                                            } else {
                                                // End date is empty, check if start date has passed
                                                LocalDate sessionStartDate = LocalDate.parse(startDate);
                                                if (sessionStartDate.isBefore(min)) {
                                                    // The start date of the tour has passed, cancel the tour
                                                    cancelSession(startDate, false);
                                                    continue; // Skip to the next tour
                                                }
                                            }


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
                                                        int packageColor = 0;
                                                        // Assuming color is stored as an integer in the 'color' field
                                                        if (sessionPackageDocumentSnapshot.contains("packageColor")) {
                                                            packageColor = sessionPackageDocumentSnapshot.getLong("packageColor").intValue();
                                                            setEventDecorator(session.getStartDate(), session.getEndDate(), packageColor);
                                                            if (currentIndex == totalSessions - 1)
                                                                calendarLoadingOverlay.setVisibility(View.GONE);

                                                        }
                                                        else {
                                                            if (isAdded()){
                                                                packageColor = requireContext().getResources().getColor(R.color.orange_primary);
                                                                setEventDecorator(session.getStartDate(), session.getEndDate(), packageColor);
                                                                // Check if this is the last session and remove loading overlay
                                                                if (currentIndex == totalSessions - 1)
                                                                    calendarLoadingOverlay.setVisibility(View.GONE);

                                                            }
                                                        }
                                                        session.setColor(packageColor);
                                                    }
                                                }).addOnFailureListener(Throwable::printStackTrace);
                                            }
                                        }
                                        // Save Active Tours to the data cache
                                        dataCache.put("activeSessions", activeSessions);
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("DATABASE", "Error getting active sessions", e);
                                    });
                        }

                        else {
                            sessionPackagesORbookedSessionsHeadline.setText(R.string.my_booked_sessions);
                            bookedSessionsRefs = new ArrayList<>();
                            db.collection("users")
                                    .document(userId)
                                    .collection("bookedSessions")
                                    .get()
                                    .addOnSuccessListener(queryDocumentSnapshots -> {
                                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                                            DocumentReference sessionRef = document.getDocumentReference("sessionRef");
                                            bookedSessionsRefs.add(sessionRef);
                                        }
                                        // Set up event decorators
                                        int index = 0; // Initialize an index counter before the loop
                                        int totalSessions = bookedSessionsRefs.size(); // Get the total number of tours
                                        if(totalSessions == 0)
                                            calendarLoadingOverlay.setVisibility(View.GONE);


                                        for (DocumentReference sessionRef : bookedSessionsRefs) {
                                            final int currentIndex = index++; // Use a final variable to use inside the lambda expression

                                            // Handle failure to fetch color from Firestore
                                            sessionRef.get().addOnSuccessListener(sessionDocumentSnapshot -> {
                                                if (isAdded() && sessionDocumentSnapshot.exists()) {
                                                    DocumentReference sessionPackageRef = sessionDocumentSnapshot.getDocumentReference("sessionPackageRef");
                                                    if (isAdded() && sessionPackageRef != null) {
                                                        sessionPackageRef.get().addOnSuccessListener(sessionPackageDocumentSnapshot -> {
                                                            if (isAdded() && sessionPackageDocumentSnapshot.exists()) {
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
                                                                            // Check if this is the last tour and remove loading overlay
                                                                            if (currentIndex == totalSessions - 1)
                                                                                calendarLoadingOverlay.setVisibility(View.GONE);

                                                                        }
                                                                        else {
                                                                            if (isAdded()){
                                                                                sessionRef.delete().addOnSuccessListener(aVoid -> {
                                                                                            // Refresh the fragment after deleting the tour reference
                                                                                            AppUtils.switchFragment(ThisProfileFragment.this, new ThisProfileFragment());
                                                                                        })
                                                                                        .addOnFailureListener(e -> {
                                                                                            Log.e("DATABASE", "Error deleting sessionRef document", e);
                                                                                        });

                                                                                db.collection("users")
                                                                                        .document(userId)
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
                                                                            // Check if this is the last tour and remove loading overlay
                                                                            if (currentIndex == totalSessions - 1)
                                                                                calendarLoadingOverlay.setVisibility(View.GONE);

                                                                        }
                                                                        else {
                                                                            if(isAdded()) {

                                                                                sessionRef.delete().addOnSuccessListener(aVoid -> {
                                                                                            // Refresh the fragment after deleting the tour reference
                                                                                            AppUtils.switchFragment(ThisProfileFragment.this, new ThisProfileFragment());
                                                                                        })
                                                                                        .addOnFailureListener(e -> {
                                                                                            Log.e("DATABASE", "Error deleting sessionRef document", e);
                                                                                        });

                                                                                db.collection("users")
                                                                                        .document(userId)
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
                                                                    AppUtils.switchFragment(ThisProfileFragment.this, new ThisProfileFragment());
                                                                })
                                                                .addOnFailureListener(e -> {
                                                                    Log.e("DATABASE", "Error deleting sessionRef document", e);
                                                                });

                                                        db.collection("users")
                                                                .document(userId)
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

    /**
     * Load user data from cache.
     */
    private boolean loadDataFromCache(View view) {
        if(isAdded()) {
            // Check if data exists in memory cache
            calendarLoadingOverlay.setVisibility(View.VISIBLE);
            userDataLoadingOverlay.setVisibility(View.VISIBLE);

            if (dataCache.get("username") != null && dataCache.get("bio") != null
                    && dataCache.get("birthDate") != null && dataCache.get("gender") != null
                    && dataCache.get("type") != null && dataCache.get("userTypeStr") != null) {

                // Load data from memory cache
                username.setText((String) dataCache.get("username"));
                if (!dataCache.get("bio").equals("")) {
                    bio.setText((String) dataCache.get("bio"));
                    bio.setVisibility(View.VISIBLE);
                }

                birthDate.setText((String) dataCache.get("birthDate"));
                gender.setText((String) dataCache.get("gender"));
                type.setText((String) dataCache.get("type"));
                userType = (String) dataCache.get("userTypeStr"); // in case the type is in different language

                // Load the profilePic
                if (dataCache.get("profilePicUrl") != null) {
                    // Load profile picture from memory cache (if available)
                    String profilePicUrl = (String) dataCache.get("profilePicUrl");
                    if (profilePicUrl != null && !profilePicUrl.isEmpty()) {
                        Glide.with(view)
                                .load(Uri.parse(profilePicUrl))
                                .apply(RequestOptions.circleCropTransform())
                                .into(profilePic);
                    }
                }
                userDataLoadingOverlay.setVisibility(View.GONE);

                if (Objects.equals(userType, "Free Diver")) {
                    // Load the list of tour package IDs and set up the RecyclerView
                    sessionPackagesIdList = (List<String>) dataCache.get("sessionPackagesIdList");
                    if (isAdded() && sessionPackagesIdList != null) {
                        sessionPackageRecyclerViewAdapter = new SessionPackageRecyclerViewAdapter(getActivity(), userId, sessionPackagesIdList, true, this);
                        recyclerViewSessions.setAdapter(sessionPackageRecyclerViewAdapter);
                        recyclerViewSessions.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
                    } else
                        return false;

                    // Load the active tours to the calendar
                    if (dataCache.get("activeSessions") != null) {
                        activeSessions = (List<ActiveSession>) dataCache.get("activeSessions");

                        for (ActiveSession session : activeSessions)
                            setEventDecorator(session.getStartDate(), session.getEndDate(), session.getColor());
                        calendarLoadingOverlay.setVisibility(View.GONE);

                    } else
                        return false;


                } else {
                    if (isAdded()) {
                        sessionPackagesORbookedSessionsHeadline.setText(R.string.my_booked_sessions);
                        // Load the active tours to the calendar
                        if (dataCache.get("bookedSessions") != null) {
                            bookedSessionsRefs = (List<DocumentReference>) dataCache.get("bookedSessions");
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
                                                if (isAdded() && sessionPackageDocumentSnapshot.exists()) {
                                                    int packageColor = requireContext().getResources().getColor(R.color.orange_primary);
                                                    String startDate, endDate;
                                                    // Assuming color is stored as an integer in the 'packageColor' field
                                                    if (sessionPackageDocumentSnapshot.contains("packageColor")) {
                                                        packageColor = sessionPackageDocumentSnapshot.getLong("packageColor").intValue();
                                                    }

                                                    if (sessionDocumentSnapshot.contains("startDate")) {
                                                        startDate = sessionDocumentSnapshot.getString("startDate");
                                                        if (sessionDocumentSnapshot.contains("endDate"))
                                                            endDate = sessionDocumentSnapshot.getString("endDate");
                                                        else
                                                            endDate = "";
                                                        setEventDecorator(startDate, endDate, packageColor);
                                                    }

                                                    // New block to check if this is the last tour
                                                    if (currentIndex == totalSessions - 1)
                                                        // This is the last tour, so remove the loading overlay
                                                        calendarLoadingOverlay.setVisibility(View.GONE);

                                                }
                                            }).addOnFailureListener(e -> {
                                                // Handle failure to fetch sessionPackageRef document
                                                Log.e("DATABASE", "Error getting sessionPackageRef document", e);
                                            });
                                        } else
                                            Log.e("DATABASE", "sessionPackageRef is null");
                                    }
                                }).addOnFailureListener(e -> {
                                    // Handle failure to fetch sessionRef document
                                    Log.e("DATABASE", "Error getting sessionRef document", e);
                                });
                            }
                        }
                    }
                }

                return true; // Data loaded from memory cache
            }
        }
        return false; // Data not found in memory cache
    }

    /**
     * Save user data to cache.
     */
    private void saveDataToCache(Uri profilePicUri) {
        if (isAdded()) {
            // Save data to memory cache
            dataCache.put("username", username.getText().toString());
            dataCache.put("bio", bio.getText().toString());
            dataCache.put("birthDate", birthDate.getText().toString());
            dataCache.put("gender", gender.getText().toString());
            dataCache.put("type", type.getText().toString());
            dataCache.put("userTypeStr", userType); // in case the type is in different language

            if (profilePicUri != null)
                dataCache.put("profilePicUrl", profilePicUri.toString());
            if(Objects.equals(userType, "Free Diver"))
                dataCache.put("sessionPackagesIdList", sessionPackagesIdList);
        }
    }

    @Override
    public void onLoadComplete() {

    }
}
