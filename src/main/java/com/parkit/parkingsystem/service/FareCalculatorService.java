package com.parkit.parkingsystem.service;

import com.parkit.parkingsystem.constants.Fare;
import com.parkit.parkingsystem.model.Ticket;

public class FareCalculatorService {

    public void calculateFare(Ticket ticket) {
        calculateFare(ticket, false);
    }

    public void calculateFare(Ticket ticket, boolean discount) {
        if (ticket.getOutTime() == null || ticket.getOutTime().before(ticket.getInTime())) {
            throw new IllegalArgumentException("Out time provided is incorrect");
        }

        long durationInMillis = ticket.getOutTime().getTime() - ticket.getInTime().getTime();

        // First 30min are free from charge
        if (durationInMillis <= 30 * 60 * 1000) {
            ticket.setPrice(0);
        } else {
            double price = getPrice(ticket, discount, durationInMillis);
            ticket.setPrice(price);
        }
    }

    private double getPrice(Ticket ticket, boolean discount, long durationInMillis) {
        double durationInHours = durationInMillis / (1000.0 * 60 * 60);
        double price;

        switch (ticket.getParkingSpot().getParkingType()) {
            case CAR:
                price = durationInHours * Fare.CAR_RATE_PER_HOUR;
                break;
            case BIKE:
                price = durationInHours * Fare.BIKE_RATE_PER_HOUR;
                break;
            default:
                throw new IllegalArgumentException("Unknown Parking Type");
        }

        if (discount) {
            price *= 0.95; // 5% discount if this vehicle came more than once already
        }
        return price;
    }
}