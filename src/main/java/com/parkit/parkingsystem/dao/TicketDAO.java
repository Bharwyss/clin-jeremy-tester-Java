package com.parkit.parkingsystem.dao;

import com.parkit.parkingsystem.config.DataBaseConfig;
import com.parkit.parkingsystem.constants.DBConstants;
import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;

public class TicketDAO {

    private static final Logger logger = LogManager.getLogger("TicketDAO");

    public DataBaseConfig dataBaseConfig = new DataBaseConfig();

    // Using a try with resource to close automatically resources
    public boolean saveTicket(Ticket ticket) {
        try (Connection con = dataBaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(DBConstants.SAVE_TICKET)) {

            ps.setInt(1, ticket.getParkingSpot().getId());
            ps.setString(2, ticket.getVehicleRegNumber());
            ps.setDouble(3, ticket.getPrice());
            ps.setTimestamp(4, new Timestamp(ticket.getInTime().getTime()));
            ps.setTimestamp(5, ticket.getOutTime() == null ? null :
                    new Timestamp(ticket.getOutTime().getTime()));

            return ps.executeUpdate() == 1; //if one line got affected, return true, if not, return false
        } catch (Exception e) {
            logger.error("Error saving ticket", e);
            throw new RuntimeException(e);
        }
    }

    public Ticket getTicket(String vehicleRegNumber) {
        try (Connection con = dataBaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(DBConstants.GET_TICKET)) {
            ps.setString(1, vehicleRegNumber);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Ticket ticket = new Ticket();
                    ParkingSpot parkingSpot =
                            new ParkingSpot(rs.getInt(1),
                                    ParkingType.valueOf(rs.getString(6)),
                                    false);
                    ticket.setParkingSpot(parkingSpot);
                    ticket.setId(rs.getInt(2));
                    ticket.setVehicleRegNumber(vehicleRegNumber);
                    ticket.setPrice(rs.getDouble(3));
                    ticket.setInTime(rs.getTimestamp(4));
                    ticket.setOutTime(rs.getTimestamp(5));
                    return ticket;
                }
            }
        } catch (Exception e) {
            logger.error("Error getting a ticket for vehicle {}", vehicleRegNumber, e);
            throw new RuntimeException(e);
        }
        return null;
    }

    public boolean updateTicket(Ticket ticket) throws SQLException, ClassNotFoundException {
        if (ticket.getOutTime() == null) {
            throw new IllegalArgumentException("outTime cannot be null");
        }

        try (Connection con = dataBaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(DBConstants.UPDATE_TICKET)) {

            ps.setDouble(1, ticket.getPrice());
            if (ticket.getOutTime() == null) throw new IllegalArgumentException("outTime cannot be null");
            ps.setTimestamp(2, new Timestamp(ticket.getOutTime().getTime()));
            ps.setInt(3, ticket.getId());

            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            logger.error("Error updating ticket", e);
            throw new RuntimeException(e);
        }
    }

    public int getNbTicket(String vehicleRegNumber) {
        try (Connection con = dataBaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(DBConstants.GET_NB_TICKET)) {

            ps.setString(1, vehicleRegNumber);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            logger.error("Error counting tickets for vehicle {}", vehicleRegNumber, e);
            throw new RuntimeException(e);
        }
    }
}
