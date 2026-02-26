package com.parkit.parkingsystem.integration;

import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.integration.config.DataBaseTestConfig;
import com.parkit.parkingsystem.integration.service.DataBasePrepareService;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ParkingDataBaseIT {

    private static DataBaseTestConfig dataBaseTestConfig = new DataBaseTestConfig();
    private static ParkingSpotDAO parkingSpotDAO;
    private static TicketDAO ticketDAO;
    private static DataBasePrepareService dataBasePrepareService;

    @Mock
    private static InputReaderUtil inputReaderUtil;

    @BeforeAll
    public static void setUp() throws Exception {
        parkingSpotDAO = new ParkingSpotDAO();
        parkingSpotDAO.dataBaseConfig = dataBaseTestConfig;
        ticketDAO = new TicketDAO();
        ticketDAO.dataBaseConfig = dataBaseTestConfig;
        dataBasePrepareService = new DataBasePrepareService();
    }

    @BeforeEach
    public void setUpPerTest() throws Exception {
        when(inputReaderUtil.readSelection()).thenReturn(1);
        when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");
        dataBasePrepareService.clearDataBaseEntries();
    }

    @AfterAll
    public static void tearDown() {

    }

    @Test
    public void testParkingACar() throws SQLException, ClassNotFoundException {
        // Checking if the parking spot is available
        int firstAvailable = parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR);

        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        parkingService.processIncomingVehicle();

        // Getting the vehicule ticket then compare its information with the DB
        Ticket savedTicket = ticketDAO.getTicket("ABCDEF");

        assertNotNull(savedTicket);
        assertEquals("ABCDEF", savedTicket.getVehicleRegNumber());
        assertNull(savedTicket.getOutTime());

        // Checking if the spot is now unavailable
        int nextAvailable = parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR);
        assertNotEquals(firstAvailable, nextAvailable);
    }

    @Test
    public void testParkingLotExit() throws Exception {
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

        // 1. Vehicle incoming, we don't call the other test to do that as each tests have to be separated
        parkingService.processIncomingVehicle();

        // 2. updateTicket can't modify inTime, directly doing that in the DB
        try (Connection con = dataBaseTestConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE ticket SET IN_TIME = ? WHERE VEHICLE_REG_NUMBER = ? AND OUT_TIME IS NULL"
             )) {
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis() - 60 * 60 * 1000));
            ps.setString(2, "ABCDEF");
            ps.executeUpdate();
        }

        // 3. Exiting Vehicle
        parkingService.processExitingVehicle();

        // 4. Checking known data : inTime is now one hour prior outTime so the price isn't 0
        Ticket updatedTicket = ticketDAO.getTicket("ABCDEF");
        assertTrue(updatedTicket.getPrice() > 0);
        assertNotNull(updatedTicket.getOutTime());
        assertTrue(updatedTicket.getOutTime().after(updatedTicket.getInTime()));
    }

    @Test
    public void testParkingLotExitRecurringUser() throws SQLException, ClassNotFoundException {
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

        // 1. Simulating an old for the tested vehicle
        try (Connection con = dataBaseTestConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO ticket (PARKING_NUMBER, VEHICLE_REG_NUMBER, PRICE, IN_TIME, OUT_TIME) VALUES (1, ?, 1.5, ?, ?)"
             )) {
            Timestamp past = new Timestamp(System.currentTimeMillis() - 2 * 60 * 60 * 1000);
            Timestamp pastOut = new Timestamp(System.currentTimeMillis() - 1 * 60 * 60 * 1000);
            ps.setString(1, "ABCDEF");
            ps.setTimestamp(2, past);
            ps.setTimestamp(3, pastOut);
            ps.executeUpdate();
        }

        // 2. Second entry for this vehicle
        parkingService.processIncomingVehicle();

        // 3. Changing inTime to have price to compare with discount
        try (Connection con = dataBaseTestConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE ticket SET IN_TIME = ? WHERE VEHICLE_REG_NUMBER = ? AND OUT_TIME IS NULL"
             )) {
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis() - 60 * 60 * 1000));
            ps.setString(2, "ABCDEF");
            ps.executeUpdate();
        }

        // 3. Exiting vehicle
        parkingService.processExitingVehicle();

        // 4. The price should be lower than a regular
        Ticket updatedTicket = ticketDAO.getTicket("ABCDEF"); // A change was needed in the DB_Constant to return the newer ticket and not the older one
        double fullPrice = 1.5;
        assertTrue(updatedTicket.getPrice() > 0);
        assertTrue(updatedTicket.getPrice() < fullPrice);
    }
}
