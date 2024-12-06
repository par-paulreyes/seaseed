package com.seass.seaseedapp.utils;

import com.google.firebase.firestore.DocumentReference;

/**
 * ActiveTour represents a tour that is currently active or ongoing.
 */
public class ActiveSession {
    String startDate; // Start date of the tour
    String endDate; // End date of the tour
    DocumentReference sessionPackageRef; // Reference to the tour package in Firestore
    int color; // Color associated with the tour

    /**
     * Constructor for creating an ActiveTour object.
     * @param startDate The start date of the tour.
     * @param endDate The end date of the tour.
     * @param sessionPackageRef Reference to the tour package in Firestore.
     */
    public ActiveSession(String startDate, String endDate, DocumentReference sessionPackageRef) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.sessionPackageRef = sessionPackageRef;
    }

    /**
     * Get the reference to the tour package in Firestore.
     * @return The DocumentReference object representing the tour package.
     */
    public DocumentReference getSessionPackageRef() {
        return sessionPackageRef;
    }

    /**
     * Get the start date of the tour.
     * @return The start date of the tour.
     */
    public String getStartDate() {
        return startDate;
    }

    /**
     * Get the end date of the tour.
     * @return The end date of the tour.
     */
    public String getEndDate() {
        return endDate;
    }

    /**
     * Set the color associated with the tour.
     * @param color The color value to set.
     */
    public void setColor(int color){
        this.color = color;
    }

    /**
     * Get the color associated with the tour.
     * @return The color value associated with the tour.
     */
    public int getColor(){
        return color;
    }
}
